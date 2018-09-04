package com.fly.core.io;

import com.fly.core.text.json.StripJsonComment;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.logging.Level;

public class IOUtils {

    private final static Charset utf8 = Charset.forName("utf-8");

    public static String getFileSize(long size) {
        if (size <= 0)
            return "0";

        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String read(File file, Charset charset) throws IOException
    {
        BufferedSource source = Okio.buffer(Okio.source(file));
        String str = source.readString(charset);
        source.close();
        return str;
    }

    public static StringBuffer readBytes(File file) throws IOException
    {
        BufferedSource source = Okio.buffer(Okio.source(file));
        StringBuffer buffer = new StringBuffer();
        byte[] bytes =  source.readByteArray();
        buffer.append(bytes);
        source.close();

        return buffer;
    }

    public static String readJson(File file) throws IOException
    {
        return StripJsonComment.strip(readUtf8(file));
    }

    public static String readUtf8(File file) throws IOException
    {
        return read(file, utf8);
    }

    public static void write(File file, String content, Charset charset) throws IOException
    {
        BufferedSink sink = Okio.buffer(Okio.sink(file));
        sink.writeString(content, charset);
        sink.close();
    }

    public static void writeUtf8(File file, String content) throws IOException
    {
        write(file, content, utf8);
    }

    public static void write(File file, byte[] bytes) throws IOException
    {
        BufferedSink sink = Okio.buffer(Okio.sink(file));
        sink.write(bytes);
        sink.close();
    }

    public static void append(File file, String content, Charset charset) throws IOException
    {
        BufferedSink sink = Okio.buffer(Okio.appendingSink(file));
        sink.writeString(content, charset);
        sink.close();
    }

    public static void appendUtf8(File file, String content) throws IOException
    {
        append(file, content, utf8);
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
