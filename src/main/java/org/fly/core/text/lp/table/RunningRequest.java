package org.fly.core.text.lp.table;

import com.sun.istack.NotNull;

import org.fly.core.function.Consumer;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.TimerTask;

public class RunningRequest
{
    private Connection connection;
    private TimerTask task = null;
    private Request request;
    private Table.IListener listener;
    private boolean replied = false;

    public RunningRequest(Connection connection, @NotNull Request request, @NotNull Table.IListener listener) {
        this.connection = connection;
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
            task = connection.getTable().getTimers().schedule(connection, new Consumer<Connection>() {
                @Override
                public void accept(Connection connection) {
                    callFail(new SocketTimeoutException("Table waited for receiving timeout"));
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

        connection.removeRunningRequest(request.getAck());
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public int writeChannel(ByteBuffer buffer) throws IOException {
        return connection.getChannel().write(buffer);
    }

    public Connection getConnection() {
        return connection;
    }
}
