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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Connection {
    private static final String TAG = Connection.class.getSimpleName();

    private int ACK = 0;
    private Package tcpPackage = null;
    private Decryptor decryptor = new Decryptor();
    private Table table;
    private String host;
    private int port;
    private long lastResponseTime = 0;
    private long lastRequestTime = 0;
    private SocketChannel channel;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private Table.IConnectionListener connectionListener;
    private Map<Integer, RunningRequest> runningRequests = new HashMap<>();
    private Map<Integer, Table.IListener> globalListeners = new HashMap<>();
    private CountDownLatch countDownLatch;

    private IoBuffer session = IoBuffer.allocateDirect(ByteBufferPool.BUFFER_SIZE);

    public Connection(Table table, String host, int port) {
        this.host = host;
        this.port = port;
        this.table = table;
    }

    public long getLastResponseTime() {
        return lastResponseTime;
    }

    public long getLastRequestTime() {
        return lastRequestTime;
    }

    public void touchResponseTime()
    {
        lastResponseTime = System.currentTimeMillis();
    }

    public void touchRequestTime()
    {
        lastRequestTime = System.currentTimeMillis();
    }

    public boolean isConnected() {
        return connected.get();
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

    synchronized void doConnect() throws IOException
    {
        InetSocketAddress address = new InetSocketAddress(host, port);

        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.register(table.getSelector(), SelectionKey.OP_CONNECT, this);
        channel.connect(address);

        sendEncryptedKey();
    }

    synchronized void doClose()
    {
        try {

            connected.set(false);

            table.getTimers().cancel(this);
            table.removeSendRequests(this);

            if (null != countDownLatch)
                countDownLatch.countDown();
            countDownLatch = null;

            if (null != channel)
                channel.close();

            channel = null;

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
    synchronized public void send(Request request, @NotNull final Table.IListener callback)
    {
        int ack = generateAck();
        request.setAck(ack);
        request.setConnection(this);

        RunningRequest runningRequest = new RunningRequest(this, request, callback);
        runningRequests.put(ack, runningRequest);

        table.send(runningRequest);
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
            public void onResponse(Response response) {
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

    public void removeRunningRequest(int ack) {
        runningRequests.remove(ack);
    }

    synchronized public void onConnected() {
        connected.set(true);

        if (null != connectionListener) {
            connectionListener.onConnected();
        }
    }

    synchronized public void onDisconnected(Throwable e) {
        doClose();

        if (null != connectionListener) {
            connectionListener.onDisconnected(e);
        }
    }

    synchronized public void onError(Throwable e) {
        if (null != connectionListener) {
            connectionListener.onError(e);
        }
    }

    synchronized public void onRecv(ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() > session.remaining()) {

            int delta = (int)Math.ceil((double)byteBuffer.remaining() / (double)session.capacity());

            if (delta > 0)
                session.extend(delta * ByteBufferPool.BUFFER_SIZE);
        }

        session.put(byteBuffer);

        touchResponseTime();

        updateSession(session);
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
                globalListeners.get(protocol).onResponse(response);
            }

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public Table getTable() {
        return table;
    }
}
