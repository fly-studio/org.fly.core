package org.fly.core.text.lp.table;

import org.fly.core.function.Consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class Timers {
    private List<TimerTask> timerTaskList = new ArrayList<>();
    private Timer timer = new Timer();

    public TimerTask schedule(Connection connection, Consumer<Connection> callback, long delay)
    {
        TimerTask timerTask = new TimerTask(connection, callback);

        timerTaskList.add(timerTask);

        timer.schedule(timerTask, delay);

        return timerTask;
    }

    public TimerTask schedule(Connection connection, Consumer<Connection> callback, long delay, long loopDelay)
    {
        TimerTask timerTask = new TimerTask(connection, callback);

        timerTaskList.add(timerTask);

        timer.schedule(timerTask, delay, loopDelay);

        return timerTask;
    }

    public void cancel(Connection connection)
    {
        for(int i = timerTaskList.size() - 1; i >=0; i--)
        {
            TimerTask task = timerTaskList.get(i);

            if (task.getConnection().equals(connection)) {
                task.cancel();
                timerTaskList.remove(i);
            }
        }
    }

    public void cancel() {
        for (TimerTask task: timerTaskList
             ) {
            task.cancel();
        }
        timerTaskList.clear();
    }


    private static class TimerTask extends java.util.TimerTask {

        private Connection connection;
        private Consumer<Connection> callback;

        public TimerTask(Connection connection, Consumer<Connection> callback) {
            this.connection = connection;
            this.callback = callback;
        }

        public Connection getConnection() {
            return connection;
        }

        @Override
        public void run() {
            callback.accept(connection);
        }
    }
}
