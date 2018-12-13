package org.fly.core.text.lp.table;

import com.sun.istack.Nullable;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.net.URLCodec;
import org.fly.core.io.buffer.BufferUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.text.encrytor.Encryption;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Table {
    private static final String TAG = Table.class.getSimpleName();
    private static final URLCodec urlCodec =  new URLCodec("ASCII");
    public static final int PROTOCOL_ENCRYPT = 0xffff;
    private static final Encryption.AES aes = new Encryption.AES(new byte[]{
            (byte) 0xc3, 0x20, (byte)0xaa, 0x3c, 0x2d, (byte)0x97, 0x14, (byte)0xa1,
            0x18, (byte)0xf3, 0x23, (byte)0xb3, (byte)0xaf, (byte)0xba, (byte)0x91, 0x4f,
            0x45, (byte)0xb1, 0x46, 0x30, (byte)0xbe, 0x56, 0x31, 0x66,
            (byte)0xae, (byte)0x86, (byte)0xa8, (byte)0x9e, (byte)0xee, (byte)0xb4, (byte)0x9c, (byte)0x84
    });

    private ConcurrentLinkedQueue<Connection> connections = new ConcurrentLinkedQueue<>();
    private final LinkedList<Request> sendRequestQueue = new LinkedList<>();

    private Selector selector;
    private Timer timer = new Timer();
    private Thread recvThread;
    private Thread sendThread;
    private long connectionTimeout = 5000;
    private long reponseTimeout = 5000;

    public Table() {
        try {
            selector = Selector.open();

            recvThread = readHandle();
            recvThread.start();

            sendThread = writeHandle();
            sendThread.start();

        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    public Selector getSelector() {
        return selector;
    }

    public Timer getTimer() {
        return timer;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public long getReponseTimeout() {
        return reponseTimeout;
    }

    public Table setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public Table setReponseTimeout(long reponseTimeout) {
        this.reponseTimeout = reponseTimeout;
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

    public Connection connect(String host, int port) throws IOException
    {
        final Connection connection = new Connection(this, host, port);

        connections.add(connection);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!connection.isConnected()){
                    connection.onError(new SocketTimeoutException("Connect timeout."));
                }
            }
        }, connectionTimeout);

        selector.wakeup();

        return connection;
    }

    public void close(Connection connection)
    {
        connection.close();
    }

    public void shutdown()
    {
        recvThread.interrupt();
        sendThread.interrupt();
        timer.cancel();

        for (SelectionKey key :selector.keys()
             ) {
            Connection connection = (Connection)key.attachment();

            if (connection.isConnected())
                connection.close();
        }

        try {
            selector.close();

        } catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    private Thread writeHandle() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted())
                {
                    try {
                        Request request;
                        synchronized (sendRequestQueue) {

                            while (sendRequestQueue.isEmpty())
                                sendRequestQueue.wait();

                            request = sendRequestQueue.poll();
                        }
                        if (request != null)
                        {
                            if (request.getConnection().isConnected())
                            {
                                ByteBuffer buffer = buildData(request);

                                buffer.flip();
                                try
                                {
                                    while (buffer.hasRemaining())
                                        request.getConnection().getChannel().write(buffer);
                                } catch (IOException e)
                                {
                                    request.getConnection().onDisconnected(e);
                                }
                            } else {
                                synchronized (sendRequestQueue) {
                                    // move the same connection's request to the tail
                                    LinkedList<Request> newQueue = new LinkedList<>();

                                    for (int i = sendRequestQueue.size() - 1; i >= 0; i--) {
                                        Request request1 = sendRequestQueue.get(i);
                                        if (request1.getConnection().equals(request.getConnection())) {
                                            newQueue.addFirst(request1);
                                            sendRequestQueue.remove(i);
                                        }
                                    }
                                    newQueue.addFirst(request);

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

    private Thread readHandle()
    {
        return new Thread(new Runnable() {
            @Override
            public void run() {

                Connection connection = null;

                while(!Thread.interrupted()) {
                    try {

                        while(!Thread.interrupted())
                        {   
                            connection = connections.poll();
                            if (connection != null) {
                                connection.connect();
                            }
                            else
                                break;
                        }

                        // Read
                        int readyChannels = selector.select();

                        if (readyChannels == 0) {
                            //Thread.sleep(5);
                            continue;
                        }

                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> keyIterator = keys.iterator();

                        while (keyIterator.hasNext() && !Thread.interrupted()) {
                            SelectionKey key = keyIterator.next();
                            connection = (Connection) key.attachment();


                            if (key.isValid()) {
                                keyIterator.remove();

                                if (key.isConnectable()) {
                                    if (((SocketChannel) key.channel()).finishConnect()) {
                                        key.interestOps(SelectionKey.OP_READ);

                                        connection.onConnected();
                                    }
                                } else if (key.isReadable()) {
                                    ByteBuffer byteBuffer = ByteBufferPool.acquire();
                                    int readBytes = ((SocketChannel) key.channel()).read(byteBuffer);

                                    if (readBytes == -1) {

                                        // End of stream, stop waiting until we push more data
                                        key.interestOps(0);

                                        throw new IOException("Server closed.");
                                    }

                                    byteBuffer.flip();

                                    connection.onRecv(byteBuffer);
                                }
                            }
                        }

                        connection = null;

                    } catch (IOException | ConnectionPendingException e) {
                        if (null != connection)
                            connection.onDisconnected(e);
                    }
                }
            }

        });
    }

    /**
     * Build a send TCP package
     * @param request
     * @return
     */
    public static ByteBuffer buildData(Request request)
    {
        return buildData(request.getAck(), request.getVersion(), request.getProtocol(), request.getRaw());
    }

    /**
     * Build a send TCP package
     * @param ack
     * @param version
     * @param protocol
     * @param raw
     * @return
     */
    public static ByteBuffer buildData(int ack, int version, int protocol, @Nullable byte[] raw)
    {

        int size = raw == null ? 0 : raw.length;
        ByteBuffer buffer = ByteBuffer.allocate(size + (Short.SIZE * 3 + Integer.SIZE) / Byte.SIZE);

        BufferUtils.putUnsignedShort(buffer, ack);
        BufferUtils.putUnsignedShort(buffer, version);
        BufferUtils.putUnsignedShort(buffer, protocol);
        BufferUtils.putUnsignedInt(buffer, size);

        if (raw != null)
            buffer.put(raw);

        return buffer;
    }

    public void send(Request request) {

        synchronized (sendRequestQueue)
        {
            sendRequestQueue.add(request);
            sendRequestQueue.notify();
        }
    }

    void removeSendRequest(Connection connection)
    {
        synchronized (sendRequestQueue)
        {
            for (int i = sendRequestQueue.size() - 1; i >= 0; i--) {
                Request request = sendRequestQueue.get(i);
                if (request.getConnection().equals(connection)) {
                    sendRequestQueue.remove(i);
                }
            }
            sendRequestQueue.notify();
        }
    }

    public interface IListener {
        void onSuccess(Response response);
        void onFail(Request request, Throwable e);
    }

    public interface IConnectionListener {
        void onConnected();
        void onDisconnected(Throwable e);
    }

}
