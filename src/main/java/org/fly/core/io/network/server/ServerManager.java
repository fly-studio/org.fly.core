package org.fly.core.io.network.server;

import org.fly.core.function.FunctionUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.network.base.BaseClient;
import org.fly.core.io.network.base.BaseManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class ServerManager<T extends Client> extends BaseManager {
    private ServerSocketChannel serverSocketChannel;
    private String host;
    private int port;
    private Class<T> clientClass;

    public ServerManager(String host, int port, Class<T> clientClass) {
        super();
        this.host = host;
        this.port = port;
        this.clientClass = clientClass;
    }

    /**
     * 检查端口是否已经被占用
     *
     * @param port
     * @return
     */
    private static boolean isPortBinded(int port) {
        boolean isBinded = false;
        Socket sc = new Socket();
        try {
            //连接本地端口，设置半秒超时
            sc.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 500);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //判断是否成功连接
            if (sc.isConnected()) {
                isBinded = true;
            }
            try {
                sc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return isBinded;
    }

    public void start() throws IOException
    {
        if (isPortBinded(port))
            throw new PortUnreachableException("Server port: " + port + " had be used by other application.");

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(host, port), Integer.MAX_VALUE);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        selector.wakeup();
    }

    public void stop() throws IOException
    {
        for (SelectionKey key :selector.keys()
                ) {
            BaseClient client = (BaseClient)key.attachment();

            if (client != null && client.isConnected())
                close(client);
        }

        serverSocketChannel.close();
        serverSocketChannel = null;
    }

    @Override
    public void shutdown() {
        try {
            stop();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        super.shutdown();
    }

    protected Thread readHandle()
    {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                Client client = null;
                while(!Thread.interrupted())
                {
                    try {
                        int readyChannels = selector.select();

                        if (readyChannels == 0) {
                            //Thread.sleep(5);
                            continue;
                        }

                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> keyIterator = keys.iterator();

                        while (keyIterator.hasNext() && !Thread.interrupted()) {
                            SelectionKey key = keyIterator.next();

                            if (key.isValid()) {
                                keyIterator.remove();

                                if (key.isAcceptable())
                                {
                                    SocketChannel channel = serverSocketChannel.accept();
                                    channel.configureBlocking(false);

                                    client = (Client) FunctionUtils.newInstance(clientClass, ServerManager.this, channel);

                                    client.doConnect();
                                    channel.register(selector, SelectionKey.OP_READ, client);

                                    client.onConnected();

                                } else if (key.isReadable())
                                {
                                    client = (Client) key.attachment();

                                    ByteBuffer byteBuffer = ByteBufferPool.acquire();

                                    int readBytes = ((SocketChannel) key.channel()).read(byteBuffer);
                                    if (readBytes == -1) {
                                        // End of stream, stop waiting until we push more data
                                        key.interestOps(0);

                                        throw new IOException("Client closed.");
                                    }

                                    byteBuffer.flip();

                                    client.onRecv(byteBuffer);
                                }
                            }

                            client = null;
                        }
                    } catch (IOException e)
                    {
                        if (null != client)
                            client.onDisconnected(e);
                    }

                }
            }
        });
    }
}
