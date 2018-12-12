package org.fly.core.text.lp.table;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import org.fly.core.io.buffer.IoBuffer;
import org.fly.core.io.protobuf.ProtobufParser;
import org.fly.core.text.lp.Decryptor;
import org.fly.core.text.lp.result.ResultProto;

public class ProtocolParser<T extends Message> {
    private static final String TAG = ProtocolParser.class.getSimpleName();

    private ProtobufParser parser;
    private Decryptor decryptor;

    public ProtocolParser(Class<T> clazz, Decryptor decryptor) {
        this.parser = new ProtobufParser<>(clazz);
        this.decryptor = decryptor;
    }

    public Message parse(IoBuffer buffer)
    {
        try
        {
            Message message = parser.deserialize(buffer);

            // Decode
            if (message instanceof ResultProto.Output)
            {
                ResultProto.Output output = ((ResultProto.Output) message);

                if (!output.getEncrypted().isEmpty())
                {
                    message = output.toBuilder().setData(
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

        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}