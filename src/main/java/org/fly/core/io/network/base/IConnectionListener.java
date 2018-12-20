package org.fly.core.io.network.base;

public interface IConnectionListener {
    void onConnected();
    void onDisconnected(Throwable e);
    void onError(Throwable e);
}
