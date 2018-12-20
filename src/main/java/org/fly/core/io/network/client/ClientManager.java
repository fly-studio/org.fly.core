package org.fly.core.io.network.client;

import org.fly.core.function.Consumer;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.network.base.BaseClient;
import org.fly.core.io.network.base.BaseManager;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientManager extends BaseManager {
    private static final String TAG = ClientManager.class.getSimpleName();

    private ConcurrentLinkedQueue<Client> clients = new ConcurrentLinkedQueue<>();

    public ClientManager() {
        super();

    }

    public Client buildClient(String host, int port)
    {
        return new Client(this, host, port);
    }

    public void connect(final Client client)
    {
        clients.add(client);

        //connecting timer
        if (connectionTimeout > 0)
        {
            timers.schedule(client, new Consumer<BaseClient>() {
                @Override
                public void accept(BaseClient client1) {
                    if (!client1.isConnected()){
                        client1.onError(new SocketTimeoutException("Connecting timeout."));
                    }
                }
            }, connectionTimeout);
        }

        selector.wakeup();
    }

    public void reconnect(Client client)
    {
        close(client);
        connect(client);
    }

    // Receive
    protected Thread readHandle()
    {
        return new Thread(new Runnable() {
            @Override
            public void run() {

                Client client = null;

                while(!Thread.interrupted()) {
                    try {

                        // connecting
                        while(!Thread.interrupted())
                        {
                            System.out.println(Thread.currentThread().getName() + "  clients");
                            client = clients.poll();
                            if (client != null) {
                                try {
                                    client.doConnect();
                                } catch (IOException e)
                                {
                                    client.onError(e);
                                }
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
                            client = (Client) key.attachment();

                            if (key.isValid()) {
                                keyIterator.remove();

                                if (key.isConnectable()) {
                                    if (((SocketChannel) key.channel()).finishConnect()) {
                                        key.interestOps(SelectionKey.OP_READ);

                                        client.onConnected();
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

                                    client.onRecv(byteBuffer);
                                }
                            }

                            client = null;
                        }


                    } catch (IOException | ConnectionPendingException e) {
                        if (null != client) {
                            client.onDisconnected(e);
                        }
                    }
                }
            }

        });
    }





}
