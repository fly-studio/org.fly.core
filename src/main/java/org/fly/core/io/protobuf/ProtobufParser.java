package org.fly.core.io.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;

import org.fly.core.io.buffer.IoBuffer;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

public class ProtobufParser <T extends Message> {
    private Parser<T> parser;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ProtobufParser(Class<T> clazz) {
        try {
            parser = (Parser<T>) clazz.getMethod("parser").invoke(null);

        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
        {
            e.printStackTrace();
        }
    }

    public T deserialize(byte[] bytes) throws InvalidProtocolBufferException
    {
        return parser.parseFrom(bytes);
    }

    public T deserialize(ByteBuffer buffer) throws InvalidProtocolBufferException
    {
        return parser.parseFrom(buffer);
    }

    public T deserialize(IoBuffer buffer) throws InvalidProtocolBufferException
    {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return deserialize(bytes);
    }


}
