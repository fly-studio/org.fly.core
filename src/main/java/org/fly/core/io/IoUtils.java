package org.fly.core.io;

import okio.*;
import org.apache.commons.codec.Charsets;
import org.fly.core.function.Consumer;
import org.fly.core.text.json.StripJsonComment;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.text.DecimalFormat;

public class IoUtils {


    private static final long CHUNK_SIZE = 1024; //Same as Okio Segment.SIZE

    public static String readableSize(long size) {
        if (size <= 0)
            return "0";

        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB", "PB", "EB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String read(File file, Charset charset) throws IOException
    {
        return read(Okio.source(file), charset);
    }

    public static String read(Source source, Charset charset) throws IOException
    {
        try (BufferedSource buffer = Okio.buffer(source)) {
            return buffer.readString(charset);
        }
    }

    public static byte[] readBytes(File file) throws IOException
    {
        return readBytes(Okio.source(file));
    }

    public static byte[] readBytes(Source source) throws IOException
    {
        try (BufferedSource buffer = Okio.buffer(source)){
            return buffer.readByteArray();
        }
    }

    public static String readJson(File file) throws IOException
    {
        return StripJsonComment.strip(readUtf8(file));
    }

    public static String readJson(Source source) throws IOException
    {
        return StripJsonComment.strip(readUtf8(source));
    }

    public static String readUtf8(File file) throws IOException
    {
        return read(file, Charsets.UTF_8);
    }

    public static String readUtf8(Source source) throws IOException
    {
        return read(source, Charsets.UTF_8);
    }

    public static void write(File file, String content, Charset charset) throws IOException
    {
        write(Okio.sink(file), content, charset);
    }

    public static void write(Sink sink, String content, Charset charset) throws IOException
    {
        try (BufferedSink buffer = Okio.buffer(sink)) {
            buffer.writeString(content, charset);
        }
    }

    /**
     * Write to a sink from a source with progress
     * @param sink
     * @param source
     * @param progressCallback callback a read size
     * @return long
     * @throws IOException
     */
    public static long write(Sink sink, Source source, Consumer<Long> progressCallback) throws IOException
    {
        try (BufferedSource bufferedSource = Okio.buffer(source);
             BufferedSink bufferedSink = Okio.buffer(sink)) {
            long read, total = 0;
            while (!bufferedSource.exhausted() && (read = bufferedSource.read(bufferedSink.buffer(), CHUNK_SIZE)) != -1) {
                total += read;
                if (null != progressCallback)
                    progressCallback.accept(read);
            }

            total += bufferedSink.writeAll(bufferedSource);
            bufferedSink.flush();

            return total;
        }
    }

    /**
     * Write to a sink from a source
     *
     * @param sink
     * @param source
     * @return long
     * @throws IOException
     */
    public static long write(Sink sink, Source source) throws IOException
    {
        return write(sink, source, null);
    }

    /**
     * Write to a file from a source
     *
     * @param file
     * @param source
     * @return long
     * @throws IOException
     */
    public static long write(File file, Source source) throws IOException
    {
        return write(Okio.sink(file), source, null);
    }

    public static void writeUtf8(File file, String content) throws IOException
    {
        write(file, content, Charsets.UTF_8);
    }

    public static void writeUtf8(Sink sink, String content) throws IOException
    {
        write(sink, content, Charsets.UTF_8);
    }

    public static void write(File file, byte[] bytes) throws IOException
    {
        write(Okio.sink(file), bytes);
    }

    public static void write(Sink sink, byte[] bytes) throws IOException
    {
        try (BufferedSink buffer = Okio.buffer(sink)) {
            buffer.write(bytes);
        }
    }

    public static void append(File file, String content, Charset charset) throws IOException
    {
        try (BufferedSink sink = Okio.buffer(Okio.appendingSink(file))) {
            sink.writeString(content, charset);
        }
    }

    public static void append(File file, byte[] content) throws IOException
    {
        try (BufferedSink sink = Okio.buffer(Okio.appendingSink(file))) {
            sink.write(content);
        }
    }

    public static void appendUtf8(File file, String content) throws IOException
    {
        append(file, content, Charsets.UTF_8);
    }

    public static final void safeClose(Object closeable) throws IOException, IllegalArgumentException {

        if (closeable != null) {
            if (closeable instanceof Closeable) {
                ((Closeable) closeable).close();
            } else if (closeable instanceof Socket) {
                ((Socket) closeable).close();
            } else if (closeable instanceof ServerSocket) {
                ((ServerSocket) closeable).close();
            } else {
                throw new IllegalArgumentException("Unknown object to close");
            }
        }
    }

}
