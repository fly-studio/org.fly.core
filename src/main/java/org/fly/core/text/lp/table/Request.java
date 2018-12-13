package org.fly.core.text.lp.table;

import com.google.protobuf.Message;

public class Request {
    private static final String TAG = Request.class.getSimpleName();

    private int ack = 0;
    private int version;
    private int protocol;
    private byte[] raw;
    private Connection connection;

    public Request(int version, int protocol, byte[] raw)
    {
        this.version = version;
        this.protocol = protocol;
        this.raw = raw;
    }

    public void setAck(int ack) {
        this.ack = ack;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
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

    public byte[] getRaw() {
        return raw;
    }

    public static class Builder {
        private int version = 0;
        private int protocol = 0;
        private byte[] raw = null;

        public Builder setVersion(int version) {
            this.version = version;
            return this;
        }

        public Builder setProtocol(int protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder setRaw(byte[] raw) {
            this.raw = raw;
            return this;
        }

        public Builder setMessage(Message message) {
            this.raw = message.toByteArray();
            return this;
        }

        public Request build()
        {
            return new Request(version, protocol, raw);
        }
    }
}
