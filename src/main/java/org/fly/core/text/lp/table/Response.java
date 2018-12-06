package org.fly.core.text.lp.table;

import com.google.protobuf.Message;

import org.fly.core.io.buffer.IoBuffer;

public class Response {
    private int ack;
    private int version;
    private int protocol;
    public IoBuffer raw;
    public Message message = null;

    public Response(int ack, int version, int protocol, IoBuffer raw, Message message) {
        this.ack = ack;
        this.version = version;
        this.protocol = protocol;
        this.raw = raw;
        this.message = message;
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

    public IoBuffer getRaw() {
        return raw;
    }

    public Message getMessage() {
        return message;
    }

    public static class Builder {
        private Package tcpPackage;
        private Message message;

        public Builder setTcpPackage(Package tcpPackage) {
            this.tcpPackage = tcpPackage;
            return this;
        }

        public Builder setMessage(Message message) {
            this.message = message;
            return this;

        }

        public Response build()
        {
            return new Response(tcpPackage.getAck(), tcpPackage.getVersion(), tcpPackage.getProtocol(), tcpPackage.getBuffer(), message);
        }
    }
}
