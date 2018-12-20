package org.fly.core.io.network.base;

import com.sun.istack.Nullable;

import org.fly.core.io.buffer.BufferUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.buffer.IoBuffer;

import java.nio.ByteBuffer;

public class Package {
    public int ack;
    public int version;
    public int protocol;
    public int length;
    public IoBuffer buffer;

    private boolean completed = false;

    public Package(int ack, int version, int protocol, int length) {
        this.ack = ack;
        this.version = version;
        this.protocol = protocol;
        this.length = length;

        buffer = IoBuffer.allocate(length > 0 ? length : ByteBufferPool.BUFFER_SIZE);
    }

    public Package(IoBuffer byteBuffer)
    {
        ack = BufferUtils.getUnsignedShort(byteBuffer);
        version = BufferUtils.getUnsignedShort(byteBuffer);
        protocol = BufferUtils.getUnsignedShort(byteBuffer);
        length = byteBuffer.getInt();

        buffer = IoBuffer.allocate(length > 0 ? length : ByteBufferPool.BUFFER_SIZE);

        append(byteBuffer);
    }

    public boolean isComplete()
    {
        return completed;
    }

    public int getProtocol() {
        return protocol;
    }

    public int getAck() {
        return ack;
    }

    public int getVersion() {
        return version;
    }

    public long getLength() {
        return length;
    }

    public IoBuffer getBuffer() {
        return buffer;
    }

    public void append(byte[] data)
    {
        append(IoBuffer.wrap(data));
    }

    public void append(IoBuffer data)
    {
        int remaining = length - buffer.position();

        if (remaining >= data.remaining())
        {
            buffer.put(data);
        }
        else
        {
            byte[] bytes = new byte[remaining];
            data.get(bytes, 0, remaining);
            buffer.put(bytes);
        }

        if (buffer.position() == length)
        {
            buffer.flip();
            completed = true;
        }
    }

    public ByteBuffer toRaw()
    {
        ByteBuffer result = ByteBuffer.allocate(length + (Short.SIZE * 3 + Integer.SIZE) / Byte.SIZE);

        BufferUtils.putUnsignedShort(result, ack);
        BufferUtils.putUnsignedShort(result, version);
        BufferUtils.putUnsignedShort(result, protocol);
        BufferUtils.putUnsignedInt(result, length);

        if (length > 0)
        {
            byte[] bytes = new byte[length];
            buffer.duplicate().get(bytes, 0, length);

            result.put(bytes);
        }

        return result;
    }


    /**
     * Build a TCP package
     * @param ack
     * @param version
     * @param protocol
     * @return
     */
    public static Package build(int ack, int version, int protocol, @Nullable byte[] data)
    {
        Package aPackage = new Package(ack, version, protocol, data != null ? data.length : 0);
        aPackage.append(data);
        return aPackage;
    }

}
