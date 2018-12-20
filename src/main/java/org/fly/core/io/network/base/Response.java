package org.fly.core.io.network.base;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import org.fly.core.io.buffer.IoBuffer;
import org.fly.core.io.protobuf.ProtobufParser;
import org.fly.core.text.encrytor.Decryptor;

public class Response {
    private int ack;
    private int version;
    private int protocol;
    private IoBuffer data;
    private Decryptor decryptor;

    public Response(int ack, int version, int protocol, IoBuffer data) {
        this(ack, version, protocol, data, null);
    }

    public Response(int ack, int version, int protocol, IoBuffer data, Decryptor decryptor) {
        this.ack = ack;
        this.version = version;
        this.protocol = protocol;
        this.data = data;
        this.decryptor = decryptor;
    }

    public Response setDecryptor(Decryptor decryptor) {
        this.decryptor = decryptor;
        return this;
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

    public IoBuffer getData() {
        return data;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends Message> T getMessage(Class<T> clazz) {

        try {

            T message = new ProtobufParser<>(clazz)
                    .deserialize(data.duplicate());

            if (null != decryptor && message instanceof org.fly.core.io.network.result.ResultProto.Output)
            {
                // Decode
                org.fly.core.io.network.result.ResultProto.Output output = ((org.fly.core.io.network.result.ResultProto.Output) message);

                if (!output.getEncrypted().isEmpty())
                {
                    message = (T)output.toBuilder().setData(
                            ByteString.copyFromUtf8(
                                    decryptor.decodeData(
                                            output.getEncrypted().toByteArray(),
                                            output.getData().toByteArray()
                                    )
                            )
                    ).build();
                }
            }
            return message;
        } catch (InvalidProtocolBufferException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static class Builder {
        private Package tcpPackage;
        private Decryptor decryptor;

        public Builder setDecryptor(Decryptor decryptor) {
            this.decryptor = decryptor;
            return this;
        }

        public Builder setTcpPackage(Package tcpPackage) {
            this.tcpPackage = tcpPackage;
            return this;
        }

        public Response build()
        {
            Response response = new Response(tcpPackage.getAck(), tcpPackage.getVersion(), tcpPackage.getProtocol(), tcpPackage.getBuffer());
            response.setDecryptor(decryptor);
            return response;
        }
    }
}
