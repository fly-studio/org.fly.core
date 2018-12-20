package org.fly.core.io.network.base;

public interface IListener {
    void onResponse(Response response);
    void onFail(Request request, Throwable e);
}
