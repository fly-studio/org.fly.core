package org.fly.core.io.network.base;

import org.fly.core.function.Consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class Timers {
    private final List<TimerTask> timerTaskList = new ArrayList<>();
    private Timer timer = new Timer();

    public TimerTask schedule(BaseClient client, Consumer<BaseClient> callback, long delay)
    {
        TimerTask timerTask = new TimerTask(client, callback);

        synchronized (timerTaskList) {
            timerTaskList.add(timerTask);
        }

        timer.schedule(timerTask, delay);

        return timerTask;
    }

    public TimerTask schedule(BaseClient client, Consumer<BaseClient> callback, long delay, long loopDelay)
    {
        TimerTask timerTask = new TimerTask(client, callback);
        synchronized (timerTaskList) {
            timerTaskList.add(timerTask);
        }
        timer.schedule(timerTask, delay, loopDelay);

        return timerTask;
    }

    public void cancel(BaseClient client)
    {
        synchronized (timerTaskList) {
            for (int i = timerTaskList.size() - 1; i >= 0; i--) {
                TimerTask task = timerTaskList.get(i);

                if (task.getClient().equals(client)) {
                    task.cancel();
                    timerTaskList.remove(i);
                }
            }
        }
    }

    public void cancel() {

        synchronized (timerTaskList) {
            for (TimerTask task : timerTaskList
                    ) {
                task.cancel();
            }
            timerTaskList.clear();
        }
    }

    private static class TimerTask extends java.util.TimerTask {

        private BaseClient client;
        private Consumer<BaseClient> callback;

        public TimerTask(BaseClient client, Consumer<BaseClient> callback) {
            this.client = client;
            this.callback = callback;
        }

        public BaseClient getClient() {
            return client;
        }

        @Override
        public void run() {
            callback.accept(client);
        }
    }
}
