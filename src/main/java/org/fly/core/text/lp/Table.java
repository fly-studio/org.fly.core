package org.fly.core.text.lp;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.sun.istack.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.net.URLCodec;
import org.fly.core.io.buffer.BufferUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.buffer.IoBuffer;
import org.fly.core.io.protobuf.ProtobufParser;
import org.fly.core.text.encrytor.Encryption;
import org.fly.core.text.lp.result.ResultProto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Table {
    private static final String TAG = Table.class.getSimpleName();
    private static final URLCodec urlCodec =  new URLCodec("ASCII");
    private static final int PROTOCOL_ENCRYPT = 0xffff;

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
        try {
            Encryption.AES aes = new Encryption.AES(new byte[]{0x6c, 0x6f, 0x63, 0x61, 0x6c, 0x76, 0x70, 0x6e, 0x6c, 0x6f, 0x63, 0x61, 0x6c, 0x76, 0x70, 0x6e});
            return StringUtils.newStringUsAscii(aes.decryptFromBase64(encoded));
        } catch (Exception e)
        {
            return null;
        }
    }

    public Connection connect(String host, int port) throws IOException
    {
        Connection connection = new Connection(host, port);

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


    public static ByteBuffer buildData(int protocol, @Nullable Message message)
    {
        int size = message == null ? 0 : message.getSerializedSize();
        ByteBuffer buffer = ByteBuffer.allocate(size + (Short.SIZE + Integer.SIZE) / Byte.SIZE);

        BufferUtils.putUnsignedShort(buffer, protocol);
        BufferUtils.putUnsignedInt(buffer, size);

        if (message != null)
            buffer.put(message.toByteArray());

        return buffer;
    }

    public class Connection {
        private Package tcpPackage = null;
        private Decryptor decryptor = new Decryptor();
        private String host;
        private SocketChannel channel;
        private int port;
        private boolean connected = false;
        private IConnectionListener connectionListener;
        private ConcurrentLinkedQueue<ByteBuffer> sendDataQueue = new ConcurrentLinkedQueue<>();
        private Map<Integer, ProtocolListener> listeners = new HashMap<>();
        private IoBuffer session = IoBuffer.allocateDirect(ByteBufferPool.BUFFER_SIZE);

        public Connection(String host, int port) throws IOException {
            this.host = host;
            this.port = port;

            channel = SocketChannel.open();
            channel.configureBlocking(false);

            sendKey();
        }

        public void connect() throws IOException
        {
            InetSocketAddress address = new InetSocketAddress(host, port);
            channel.connect(address);

            channel.register(selector, SelectionKey.OP_CONNECT, this);
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnectionListener(IConnectionListener connectionListener)
        {
            this.connectionListener = connectionListener;
        }

        public <T extends Message> void addListener(int protocol, Class<T> clazz, IListener listener)
        {
            listeners.put(protocol, new ProtocolListener<>(clazz, listener, decryptor));
        }

        public void trigger(Package tcpPackage)
        {
            trigger(tcpPackage.getProtocol(), tcpPackage.getBuffer());
        }

        public void trigger(int protocol, IoBuffer message)
        {
            ProtocolListener listener = listeners.get(protocol);

            if (null != listener)
                listener.trigger(message);
        }

        public void close()
        {
            try {
                channel.close();

                connected = false;

            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        boolean hostEquals(String host, int port)
        {
            return this.host.equalsIgnoreCase(host) && port == this.port;
        }

        public void send(int protocol, Message message)
        {
            ByteBuffer buffer = buildData(protocol, message);
            buffer.flip();

            sendDataQueue.add(buffer);
            doSend();
        }

        public void send(int protocol)
        {
            send(protocol, null);
        }

        public void sendKey()
        {
            ResultProto.EncryptKey.Builder encryptBuilder = ResultProto.EncryptKey.newBuilder();
            encryptBuilder.setKey(ByteString.copyFromUtf8(decryptor.getPublicKey()));

            send(PROTOCOL_ENCRYPT, encryptBuilder.build());
        }

        public void doSend()
        {
            try {
                while(channel.isConnected())
                {
                    ByteBuffer buffer = sendDataQueue.poll();
                    if (buffer != null)
                    {
                        while (buffer.hasRemaining())
                            channel.write(buffer);
                    } else
                        break;
                }
            } catch (IOException e)
            {
                onDisconnected(e);
            }

        }

        public void onConnected() {
            if (null != connectionListener) {
                connectionListener.onConnected();
            }

            doSend();
        }

        public void onRecv(ByteBuffer byteBuffer) {
            if (byteBuffer.remaining() > session.remaining()) {

                int delta = (int)Math.ceil((double)byteBuffer.remaining() / (double)session.capacity());

                if (delta > 0)
                    session.extend(delta * ByteBufferPool.BUFFER_SIZE);
            }

            session.put(byteBuffer);

            update(session);
        }

        public void onDisconnected(Throwable e) {
            close();

            if (null != connectionListener) {
                connectionListener.onDisconnected(e);
            }

        }

        private void update(IoBuffer buffer)
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
                trigger(tcpPackage);
                tcpPackage = null;
            }
        }
    }

    private class ProtocolListener<T extends Message> {
        private final ProtobufParser parser;
        private Decryptor decryptor;
        private IListener<T> listener;

        ProtocolListener(Class<T> clazz, IListener<T> listener, Decryptor decryptor) {
            this.listener = listener;
            parser = new ProtobufParser<>(clazz);
            this.decryptor = decryptor;
        }

        void trigger(IoBuffer buffer)
        {
            try
            {
                Message message = parser.deserialize(buffer);

                // Decode
                if (message instanceof ResultProto.Output)
                {
                    ResultProto.Output output = ((ResultProto.Output) message);

                    if (!output.getEncrypted().isEmpty())
                    {
                        message = output.toBuilder().setData(
                                ByteString.copyFromUtf8(
                                        decryptor.decodeData(
                                                output.getEncrypted().toByteArray(),
                                                output.getData().toByteArray()
                                        )
                                )
                        ).build();
                    }
                }

                listener.onRead((T)message);

            } catch (Exception e)
            {

            }
        }
    }

    public interface IListener<T extends Message> {

        void onRead(T message);
    }

    public interface IConnectionListener {
        void onConnected();
        void onDisconnected(Throwable e);
    }

}
