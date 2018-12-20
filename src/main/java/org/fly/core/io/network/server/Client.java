package org.fly.core.io.network.server;

import org.fly.core.io.network.base.BaseClient;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public abstract class Client extends BaseClient {
    private static final String TAG = Client.class.getSimpleName();

    public Client(ServerManager manager, SocketChannel clientChannel) {
        super(manager);
        this.channel = clientChannel;
    }

    @Override
    public void doConnect() throws IOException {

        channel.socket().setTcpNoDelay(true);
        channel.socket().setKeepAlive(true);
        sendEncryptedKey();
    }

    @Override
    public synchronized void onConnected() {
        super.onConnected();

        manager.startIdleTimer(this);
    }

}
