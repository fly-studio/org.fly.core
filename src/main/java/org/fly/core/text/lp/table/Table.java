package org.fly.core.text.lp.table;

import com.google.protobuf.Message;
import com.sun.istack.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.net.URLCodec;
import org.fly.core.io.buffer.BufferUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.buffer.IoBuffer;
import org.fly.core.text.encrytor.Encryption;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Table {
    private static final String TAG = Table.class.getSimpleName();
    private static final URLCodec urlCodec =  new URLCodec("ASCII");
    public static final int PROTOCOL_ENCRYPT = 0xffff;
    private static final Encryption.AES aes = new Encryption.AES(new byte[]{
            0x6c, 0x6f, 0x63, 0x61,
            0x6c, 0x76, 0x70, 0x6e,
            0x6c, 0x6f, 0x63, 0x61,
            0x6c, 0x76, 0x70, 0x6e,
            0x6c, 0x6f, 0x63, 0x61,
            0x6c, 0x76, 0x70, 0x6e,
            0x6c, 0x6f, 0x63, 0x61,
            0x6c, 0x76, 0x70, 0x6e});

    private ConcurrentLinkedQueue<Connection> connections = new ConcurrentLinkedQueue<>();
    private Selector selector;
    private Thread recvThread;

    public Table() {
        try {
            selector = Selector.open();

            recvThread = readHandle();
            recvThread.start();

        } catch (IOException e)
        {

        }
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
        Connection connection = new Connection(selector, host, port);

        connections.add(connection);

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
                            if (connection != null)
                                connection.connect();
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

    public static ByteBuffer buildData(int ack, int version, int protocol, @Nullable Message message)
    {

        int size = message == null ? 0 : message.getSerializedSize();
        ByteBuffer buffer = ByteBuffer.allocate(size + (Short.SIZE + Integer.SIZE) / Byte.SIZE);

        BufferUtils.putUnsignedShort(buffer, ack);
        BufferUtils.putUnsignedShort(buffer, version);
        BufferUtils.putUnsignedShort(buffer, protocol);
        BufferUtils.putUnsignedInt(buffer, size);

        if (message != null)
            buffer.put(message.toByteArray());

        return buffer;
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
