package org.fly.core.text.lp.table;

import com.google.protobuf.ByteString;
import com.sun.istack.NotNull;

import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.buffer.IoBuffer;
import org.fly.core.text.lp.Decryptor;
import org.fly.core.text.lp.result.ResultProto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

public class Connection {
    private static final String TAG = Connection.class.getSimpleName();

    private int ACK = 0;
    private Package tcpPackage = null;
    private Decryptor decryptor = new Decryptor();
    private Table table;
    private String host;
    private int port;
    private SocketChannel channel;
    private boolean connected = false;
    private Timer timer;
    private Table.IConnectionListener connectionListener;
    private Map<Integer, RunningRequest> runningRequests = new HashMap<>();
    private Map<Integer, Table.IListener> globalListeners = new HashMap<>();
    private CountDownLatch countDownLatch;

    private IoBuffer session = IoBuffer.allocateDirect(ByteBufferPool.BUFFER_SIZE);

    public Connection(Table table, String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.table = table;
        this.timer = table.getTimer();

        sendEncryptedKey();
    }

    public void connect() throws IOException
    {
        InetSocketAddress address = new InetSocketAddress(host, port);

        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.register(table.getSelector(), SelectionKey.OP_CONNECT, this);
        channel.connect(address);
    }

    public void reconnect() throws IOException
    {
        close();
        connect();
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnectionListener(Table.IConnectionListener connectionListener)
    {
        this.connectionListener = connectionListener;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public int generateAck()
    {
        if (++ACK > 0xFFFF)
            ACK = ACK & 0xFFFF;

        return ACK;
    }

    public void close()
    {
        try {
            connected = false;

            table.removeSendRequest(this);

            if (null != countDownLatch)
                countDownLatch.countDown();
            countDownLatch = null;

            channel.close();

        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    boolean hostEquals(String host, int port)
    {
        return this.host.equalsIgnoreCase(host) && port == this.port;
    }

    /**
     * Listen all responses of the defined protocol
     * it major used for the broadcasting
     *
     * If it listen a same protocol as {@see #send(Request, callback)}, the listener will be called after the "send"'s callback
     *
     * Warning: broadcast have no Ack, but other response have an Ack as same as the request
     *
     * @param protocol
     * @param listener
     * @return
     */
    public Connection listen(int protocol, Table.IListener listener)
    {
        globalListeners.put(protocol, listener);

        return this;
    }

    /**
     * Asynchronous send a request without a callback,
     * or it's mean that the server maybe not response this request
     *
     * @param request
     */
    public void send(Request request)
    {
        send(request, null);
    }

    /**
     * Asynchronous send a request with a callback
     *
     * @param request
     * @param callback
     */
    public void send(Request request, @NotNull final Table.IListener callback)
    {
        int ack = generateAck();
        request.setAck(ack);
        request.setConnection(this);

        if (callback != null)
        {
            RunningRequest runningRequest = new RunningRequest(request, callback);
            runningRequests.put(ack, runningRequest);
        } else {
            runningRequests.remove(ack);

        }

        doSend(request);
    }

    /**
     * Synchronized Send a request, Block the Called-Thread util response
     *
     * @param request
     * @return
     * @throws Throwable
     */
    public Response sendSync(Request request) throws Throwable
    {
        if (null != countDownLatch)
            countDownLatch.countDown();

        countDownLatch = new CountDownLatch(1);

        final List<Object> result = new ArrayList<>();

        send(request, new Table.IListener() {
            @Override
            public void onSuccess(Response response) {
                countDownLatch.countDown();

                result.add(response);
            }

            @Override
            public void onFail(Request request, Throwable e) {
                countDownLatch.countDown();

                result.add(e);

                onError(e);
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e)
        {
            throw new TimeoutException(e.toString());
        }

        countDownLatch = null;

        if (result.isEmpty())
            throw new IOException("Unknown error.");

        Object first = result.get(0);
        if (first instanceof Response)
            return (Response)first;
        else
            throw (Throwable)first;
    }

    public void sendEncryptedKey()
    {
        ResultProto.EncryptKey.Builder encryptBuilder = ResultProto.EncryptKey.newBuilder();

        byte[] key = Table.encodeString(
                StringUtils.getBytesUsAscii(
                        decryptor.getPublicKey()
                )
        );

        encryptBuilder.setKey(
                ByteString.copyFrom(key)
        );

        send(new Request.Builder()
                .setProtocol(Table.PROTOCOL_ENCRYPT)
                .setMessage(encryptBuilder.build())
                .build()
        );
    }

    public void doSend(Request request)
    {
        table.send(request);
    }

    public void onConnected() {
        connected = true;

        if (null != connectionListener) {
            connectionListener.onConnected();
        }
    }

    public void onRecv(ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() > session.remaining()) {

            int delta = (int)Math.ceil((double)byteBuffer.remaining() / (double)session.capacity());

            if (delta > 0)
                session.extend(delta * ByteBufferPool.BUFFER_SIZE);
        }

        session.put(byteBuffer);

        updateSession(session);
    }

    public void onDisconnected(Throwable e) {
        close();

        if (null != connectionListener) {
            connectionListener.onDisconnected(e);
        }

    }

    // 接近粘包，分包问题
    private void updateSession(IoBuffer buffer)
    {
        //readable
        buffer.flip();

        if (tcpPackage == null)
        {
            if (buffer.remaining() < 6)
                return;

            tcpPackage = new Package(buffer);
        }
        else
        {
            tcpPackage.append(buffer);
        }

        //turn read to write
        if (buffer.position() != 0)
            buffer.compact();

        if (tcpPackage.isComplete())
        {
            System.out.print("Package complete.");
            callback(tcpPackage);

            tcpPackage = null;
        }
    }

    private void callback(Package tcpPackage)
    {
        try {

            int ack = tcpPackage.getAck();
            int protocol = tcpPackage.getProtocol();

            Response response = new Response.Builder()
                    .setTcpPackage(tcpPackage)
                    .setDecryptor(decryptor)
                    .build();

            if (runningRequests.containsKey(ack))
            {
                runningRequests.get(ack).callSuccess(response);
            }

            if (globalListeners.containsKey(protocol))
            {
                globalListeners.get(protocol).onSuccess(response);
            }

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void onError(Throwable e) {


    }

    public class RunningRequest
    {
        private TimerTask task = null;
        private Request request;
        private Table.IListener listener;

        public RunningRequest(@NotNull Request request, @NotNull Table.IListener listener) {
            this.request = request;
            this.listener = listener;
            startTimer();
        }

        private void callSuccess(Response response)
        {
            cancel();

            if (null != listener) {
                listener.onSuccess(response);
            }
        }

        private void callFail(Throwable e)
        {
            cancel();

            if (null != listener) {
                listener.onFail(request, e);
            }
        }

        private void startTimer()
        {
            if (request.getTimeout() > 0)
            {
                task = new TimerTask() {
                    @Override
                    public void run() {
                        callFail(new TimeoutException("Table waited for receiving timeout"));
                    }
                };
                timer.schedule(task, request.getTimeout());
            }
        }

        public void cancel()
        {
            if (null != task)
                task.cancel();

            task = null;

            runningRequests.remove(request.getAck());

        }

    }


}
