package org.fly.core.io.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import org.fly.core.io.buffer.IoBuffer;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ProtobufParser <T extends Message> {
    private Parser<T> parser;
    private static final Map<String, Parser> parsers = new HashMap<>();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ProtobufParser(Class<T> clazz)  {
        String name = clazz.getName();
        if (parsers.containsKey(name))
        {
            parser = parsers.get(name);

        } else {
            try {
                parser = (Parser<T>) clazz.getMethod("parser").invoke(null);
                parsers.put(name, parser);

            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
            {
                e.printStackTrace();
            }
        }
    }

    public T deserialize(byte[] bytes) throws InvalidProtocolBufferException
    {
        return parser != null ? parser.parseFrom(bytes) : null;
    }

    public T deserialize(ByteBuffer buffer) throws InvalidProtocolBufferException
    {
        return parser != null ? parser.parseFrom(buffer) : null;
    }

    public T deserialize(IoBuffer buffer) throws InvalidProtocolBufferException
    {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return deserialize(bytes);
    }


}
