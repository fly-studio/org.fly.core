package org.fly.core.io.network.base;

import com.google.protobuf.ByteString;
import com.sun.istack.NotNull;

import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.buffer.IoBuffer;
import org.fly.core.io.network.client.ClientManager;
import org.fly.core.text.encrytor.Decryptor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseClient {
    protected BaseManager manager;
    protected int ACK = 0;
    protected Package tcpPackage = null;
    protected Decryptor decryptor = new Decryptor();

    protected String host;
    protected int port;
    protected SocketChannel channel;
    protected AtomicBoolean connected = new AtomicBoolean(false);

    protected long lastResponseTime = 0;
    protected long lastRequestTime = 0;

    private IConnectionListener connectionListener;

    protected Map<Integer, RunningRequest> runningRequests = new HashMap<>();
    protected Map<Integer, IListener> globalListeners = new HashMap<>();
    protected CountDownLatch countDownLatch;

    protected IoBuffer session = IoBuffer.allocateDirect(ByteBufferPool.BUFFER_SIZE);

    public BaseClient(BaseManager manager) {
        this.manager = manager;
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

    public SocketChannel getChannel() {
        return channel;
    }

    public int generateAck()
    {
        if (++ACK > 0xFFFF)
            ACK = ACK & 0xFFFF;

        return ACK;
    }

    /**
     * Listen all responses of the defined protocol
     * it major used for the broadcasting or server's listen
     *
     * If it listen a same protocol as {@see #send(Request, callback)}, the listener will be called after the "send"'s callback
     *
     * Warning: broadcast have no Ack, but other response have an Ack as same as the request
     *
     * @param protocol
     * @param listener
     * @return
     */
    public BaseClient listen(int protocol, IListener listener)
    {
        globalListeners.put(protocol, listener);

        return this;
    }

    public void setConnectionListener(IConnectionListener connectionListener)
    {
        this.connectionListener = connectionListener;
    }

    abstract public void doConnect() throws IOException;

    synchronized public void doClose() throws IOException
    {
        connected.set(false);

        manager.getTimers().cancel(this);
        manager.removeSendRequests(this);

        if (null != countDownLatch)
            countDownLatch.countDown();
        countDownLatch = null;

        if (null != channel)
            channel.close();

        channel = null;
    }

    public void doSend(RunningRequest request) {
        manager.send(request);
    }

    public BaseManager getManager(){
        return manager;
    }


    public void sendEncryptedKey()
    {
        org.fly.core.io.network.result.ResultProto.EncryptKey.Builder encryptBuilder = org.fly.core.io.network.result.ResultProto.EncryptKey.newBuilder();

        byte[] key = ClientManager.encodeString(
                StringUtils.getBytesUsAscii(
                        decryptor.getPublicKey()
                )
        );

        encryptBuilder.setKey(
                ByteString.copyFrom(key)
        );

        send(new Request.Builder()
                .setProtocol(ClientManager.PROTOCOL_ENCRYPT)
                .setMessage(encryptBuilder.build())
                .build()
        );
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
    synchronized public void send(Request request, @NotNull final IListener callback)
    {
        int ack = generateAck();
        request.setAck(ack);
        request.setClient(this);

        RunningRequest runningRequest = new RunningRequest(this, request, callback);
        runningRequests.put(ack, runningRequest);

        doSend(runningRequest);
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

        send(request, new IListener() {
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
        try {
            doClose();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        if (null != connectionListener) {
            connectionListener.onDisconnected(e);
        }
    }

    synchronized public void onError(Throwable e) {
        if (null != connectionListener) {
            connectionListener.onError(e);
        }
    }
}
