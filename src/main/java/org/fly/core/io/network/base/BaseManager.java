package org.fly.core.io.network.base;

import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.function.Consumer;
import org.fly.core.text.encrytor.Encryption;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.LinkedList;

public abstract class BaseManager {
    private static final Encryption.AES aes = new Encryption.AES(new byte[]{
            (byte) 0xc3, 0x20, (byte)0xaa, 0x3c, 0x2d, (byte)0x97, 0x14, (byte)0xa1,
            0x18, (byte)0xf3, 0x23, (byte)0xb3, (byte)0xaf, (byte)0xba, (byte)0x91, 0x4f,
            0x45, (byte)0xb1, 0x46, 0x30, (byte)0xbe, 0x56, 0x31, 0x66,
            (byte)0xae, (byte)0x86, (byte)0xa8, (byte)0x9e, (byte)0xee, (byte)0xb4, (byte)0x9c, (byte)0x84
    });

    public static final int PROTOCOL_ENCRYPT = 0xffff;
    protected final LinkedList<RunningRequest> sendRequestQueue = new LinkedList<>();
    protected Selector selector;
    protected Timers timers = new Timers();
    protected Thread recvThread;
    protected Thread sendThread;
    protected long connectionTimeout = 5_000;
    protected long responseTimeout = 5_000;
    protected long idleTimeout = 60_000;

    public BaseManager() {
        try {
            selector = Selector.open();

        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void startSelector()
    {
        recvThread = readHandle();
        recvThread.start();

        sendThread = writeHandle();
        sendThread.start();
    }

    public Selector getSelector() {
        return selector;
    }

    public Timers getTimers() {
        return timers;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public long getResponseTimeout() {
        return responseTimeout;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public BaseManager setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public BaseManager setResponseTimeout(long responseTimeout) {
        this.responseTimeout = responseTimeout;
        return this;
    }

    public BaseManager setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    public static String decodeString(String encoded)
    {
        return decodeString(Encryption.Base64.decode(encoded));
    }

    public static String decodeString(byte[] encoded)
    {
        try {
            return StringUtils.newStringUsAscii(aes.decrypt(encoded));
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] encodeString(byte[] plaintext)
    {
        try {
            return aes.encrypt(plaintext);
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public void close(BaseClient client)
    {
        try {
            client.doClose();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void shutdown()
    {
        recvThread.interrupt();
        sendThread.interrupt();
        timers.cancel();

        for (SelectionKey key :selector.keys()
                ) {
            BaseClient client = (BaseClient)key.attachment();

            if (client != null && client.isConnected())
                close(client);
        }

        try {
            selector.close();

        } catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    public void startIdleTimer(BaseClient client)
    {
        //Idle timer
        if (idleTimeout > 0)
        {
            timers.schedule(client, new Consumer<BaseClient>() {
                @Override
                public void accept(BaseClient client1) {
                    long time = System.currentTimeMillis();
                    if (client1.isConnected()
                            && time - client1.getLastResponseTime() > idleTimeout
                            && time - client1.getLastRequestTime() > idleTimeout
                            )
                    {
                        client1.onError(new SocketTimeoutException("Idle timeout."));
                    }
                }
            }, idleTimeout, idleTimeout);
        }
    }

    protected abstract Thread readHandle();

    // Send to server
    private Thread writeHandle() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted())
                {
                    try {
                        RunningRequest runningRequest;
                        synchronized (sendRequestQueue) {

                            while (sendRequestQueue.isEmpty())
                                sendRequestQueue.wait();

                            runningRequest = sendRequestQueue.poll();
                        }
                        if (runningRequest != null)
                        {
                            if (runningRequest.isConnected())
                            {
                                ByteBuffer buffer = runningRequest.getRequest().toPackage().toRaw();

                                buffer.flip();
                                try
                                {
                                    while (buffer.hasRemaining())
                                        runningRequest.writeChannel(buffer);

                                    runningRequest.getClient().touchRequestTime();
                                    runningRequest.startTimer(responseTimeout);

                                } catch (IOException e)
                                {
                                    runningRequest.getClient().onDisconnected(e);
                                }
                            } else {
                                synchronized (sendRequestQueue) {
                                    // move the same connection's request to the tail
                                    LinkedList<RunningRequest> newQueue = new LinkedList<>();

                                    for (int i = sendRequestQueue.size() - 1; i >= 0; i--) {
                                        RunningRequest request1 = sendRequestQueue.get(i);
                                        if (request1.getClient().equals(runningRequest.getClient())) {
                                            newQueue.addFirst(request1);
                                            sendRequestQueue.remove(i);
                                        }
                                    }
                                    newQueue.addFirst(runningRequest);

                                    sendRequestQueue.addAll(newQueue);
                                }
                                Thread.sleep(10);
                            }
                        }
                    } catch (InterruptedException e)
                    {
                        break;
                    }
                }
            }
        });
    }

    public void send(RunningRequest runningRequest) {

        synchronized (sendRequestQueue)
        {
            sendRequestQueue.add(runningRequest);
            sendRequestQueue.notify();
        }
    }

    public void removeSendRequests(BaseClient client)
    {
        synchronized (sendRequestQueue)
        {
            for (int i = sendRequestQueue.size() - 1; i >= 0; i--) {
                RunningRequest runningRequest = sendRequestQueue.get(i);
                if (runningRequest.getClient().equals(client)) {
                    sendRequestQueue.remove(i);
                }
            }
            sendRequestQueue.notify();
        }
    }
}
