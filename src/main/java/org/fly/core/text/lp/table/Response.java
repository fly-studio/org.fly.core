package org.fly.core.text.lp.table;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import org.fly.core.io.buffer.IoBuffer;
import org.fly.core.io.protobuf.ProtobufParser;
import org.fly.core.text.lp.Decryptor;
import org.fly.core.text.lp.result.ResultProto;

public class Response {
    private int ack;
    private int version;
    private int protocol;
    private IoBuffer raw;
    private Decryptor decryptor;

    public Response(int ack, int version, int protocol, IoBuffer raw) {
        this(ack, version, protocol, raw, null);
    }

    public Response(int ack, int version, int protocol, IoBuffer raw, Decryptor decryptor) {
        this.ack = ack;
        this.version = version;
        this.protocol = protocol;
        this.raw = raw;
        this.decryptor = decryptor;
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends Message> T getMessage(Class<T> clazz) {

        try {

            T message = new ProtobufParser<>(clazz)
                    .deserialize(raw.duplicate());

            if (null != decryptor && message instanceof ResultProto.Output)
            {
                // Decode
                ResultProto.Output output = ((ResultProto.Output) message);

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
            return new Response(tcpPackage.getAck(), tcpPackage.getVersion(), tcpPackage.getProtocol(), tcpPackage.getBuffer(), decryptor);
        }
    }
}
