package org.fly.core.text.lp.table;

import com.google.protobuf.Message;

public class Request {
    private static final String TAG = Request.class.getSimpleName();

    private int ack = 0;
    private int version;
    private int protocol;
    private Message message;
    private long timeout;

    public Request(int version, int protocol, Message message, long timeout) {
        this.version = version;
        this.protocol = protocol;
        this.message = message;
        this.timeout = timeout;
    }

    public void setAck(int ack) {
        this.ack = ack;
    }

    public int getAck() {
        return ack;
    }

    public int getVersion() {
        return version;
    }

    public int getProtocol() {
        return protocol;
    }

    public Message getMessage() {
        return message;
    }

    public long getTimeout() {
        return timeout;
    }

    public static class Builder {
        private int version = 0;
        private int protocol = 0;
        private Message message = null;
        private long timeout = 20 * 1000L;

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public Builder setVersion(int version) {
            this.version = version;
            return this;
        }

        public Builder setProtocol(int protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder setMessage(Message message) {
            this.message = message;
            return this;
        }

        public Request build()
        {
            return new Request(version, protocol, message, timeout);
        }
    }
}
