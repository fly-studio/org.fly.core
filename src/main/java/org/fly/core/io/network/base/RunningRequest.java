package org.fly.core.io.network.base;

import com.sun.istack.NotNull;

import org.fly.core.function.Consumer;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.TimerTask;

public class RunningRequest
{
    private BaseClient client;
    private TimerTask task = null;
    private Request request;
    private IListener listener;
    private boolean replied = false;

    public RunningRequest(BaseClient client, @NotNull Request request, @NotNull IListener listener) {
        this.client = client;
        this.request = request;
        this.listener = listener;
    }

    public Request getRequest() {
        return request;
    }

    public void callSuccess(Response response)
    {
        cancel();

        setReplied(true);

        if (null != listener) {
            listener.onResponse(response);
        }
    }

    public void callFail(Throwable e)
    {
        cancel();

        if (null != listener) {
            listener.onFail(request, e);
        }
    }

    public void setReplied(boolean replied) {
        this.replied = replied;
    }

    public boolean isReplied() {
        return replied;
    }

    public void startTimer(long responseTimeout)
    {
        if (responseTimeout > 0 && listener != null)
        {
            task = client.getManager().getTimers().schedule(client, new Consumer<BaseClient>() {
                @Override
                public void accept(BaseClient connection) {
                    callFail(new SocketTimeoutException("ClientManager receiving timeout."));
                }
            }, responseTimeout);

        }
    }

    public void cancel()
    {
        if (null != task)
            task.cancel();

        task = null;

        setReplied(false);

        client.removeRunningRequest(request.getAck());
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public int writeChannel(ByteBuffer buffer) throws IOException {
        return client.getChannel().write(buffer);
    }

    public BaseClient getClient() {
        return client;
    }
}
