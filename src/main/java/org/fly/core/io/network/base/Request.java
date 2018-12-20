package org.fly.core.io.network.base;

import com.google.protobuf.Message;

public class Request {
    private static final String TAG = Request.class.getSimpleName();

    private int ack = 0;
    private int version;
    private int protocol;
    private byte[] data;
    private BaseClient client;

    public Request(int version, int protocol, byte[] data)
    {
        this.version = version;
        this.protocol = protocol;
        this.data = data;
    }

    public void setAck(int ack) {
        this.ack = ack;
    }

    public void setClient(BaseClient client) {
        this.client = client;
    }

    public BaseClient getClient() {
        return client;
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

    public Package toPackage() {
        return Package.build(getAck(), getVersion(), getProtocol(), getData());
    }

    public byte[] getData() {
        return data;
    }

    public static class Builder {
        private int version = 0;
        private int protocol = 0;
        private byte[] data = null;

        public Builder setVersion(int version) {
            this.version = version;
            return this;
        }

        public Builder setProtocol(int protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder setData(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder setMessage(Message message) {
            this.data = message.toByteArray();
            return this;
        }

        public Request build()
        {
            return new Request(version, protocol, data);
        }
    }
}
