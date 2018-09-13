package org.fly.core.text.lp;

import org.fly.core.io.buffer.BufferUtils;
import org.fly.core.io.buffer.ByteBufferPool;
import org.fly.core.io.buffer.IoBuffer;

public class Package {
    public int protocol;
    public int length;
    public IoBuffer buffer;

    private boolean completed = false;

    public Package(int protocol, int length) {
        this.protocol = protocol;
        this.length = length;

        buffer = IoBuffer.allocate(length > 0 ? length : ByteBufferPool.BUFFER_SIZE);
    }

    public Package(IoBuffer byteBuffer)
    {
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

    public long getLength() {
        return length;
    }

    public IoBuffer getBuffer() {
        return buffer;
    }

    public void append(IoBuffer byteBuffer)
    {
        int remaining = length - buffer.position();

        if (remaining >= byteBuffer.remaining())
        {
            buffer.put(byteBuffer);
        }
        else
        {
            byte[] bytes = new byte[remaining];
            byteBuffer.get(bytes, 0, remaining);
            buffer.put(bytes);
        }

        if (buffer.position() == length)
        {
            buffer.flip();
            completed = true;
        }
    }
}
