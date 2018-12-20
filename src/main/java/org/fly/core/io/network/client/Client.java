package org.fly.core.io.network.client;

import org.fly.core.io.network.base.BaseClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class Client extends BaseClient {
    private static final String TAG = Client.class.getSimpleName();

    public Client(ClientManager clientManager, String host, int port) {
        super(clientManager);

        this.host = host;
        this.port = port;
    }

    synchronized public void doConnect() throws IOException
    {
        InetSocketAddress address = new InetSocketAddress(host, port);

        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.register(manager.getSelector(), SelectionKey.OP_CONNECT, this);
        channel.connect(address);

        sendEncryptedKey();
    }

    boolean hostEquals(String host, int port)
    {
        return this.host.equalsIgnoreCase(host) && port == this.port;
    }

    @Override
    public synchronized void onConnected() {
        super.onConnected();

        manager.startIdleTimer(this);
    }
}
