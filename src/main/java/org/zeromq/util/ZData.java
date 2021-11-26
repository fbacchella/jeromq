package org.zeromq.util;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import org.zeromq.ZMQ;

public class ZData
{
    private static final String HEX_CHAR = "0123456789ABCDEF";

    private final ByteBuffer data;

    public ZData(byte[] data)
    {
        this.data = ByteBuffer.wrap(data);
    }

    public ZData(ByteBuffer data)
    {
        this.data = data.duplicate();
    }

    /**
     * String equals.
     * Uses String compareTo for the comparison (lexigraphical)
     * @param str String to compare with data
     * @return True if data matches given string
     */
    public boolean streq(String str)
    {
        return streq(data, str);
    }

    /**
     * String equals.
     * Uses String compareTo for the comparison (lexigraphical)
     * @param str String to compare with data
     * @param data the binary data to compare
     * @return True if data matches given string
     */
    public static boolean streq(byte[] data, String str)
    {
        if (data == null) {
            return false;
        }
        return new String(data, ZMQ.CHARSET).compareTo(str) == 0;
    }

    /**
     * String equals.
     * Uses String compareTo for the comparison (lexigraphical)
     * @param str String to compare with data
     * @param data the binary data to compare
     * @return True if data matches given string
     */
    public static boolean streq(ByteBuffer data, String str)
    {
        if (data == null) {
            return false;
        }
        data.mark();
        CharBuffer content = ZMQ.CHARSET.decode(data);
        data.reset();
        return str.contentEquals(content);
    }

    public boolean equals(byte[] that)
    {
        return ByteBuffer.wrap(that).equals(data);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (getClass() != other.getClass()) {
            return false;
        }
        ZData that = (ZData) other;
        return this.data.equals(that.data);
     }

    @Override
    public int hashCode()
    {
        return data.hashCode();
    }

    /**
     * Returns a human - readable representation of data
     * @return
     *          A text string or hex-encoded string if data contains any non-printable ASCII characters
     */
    public String toString()
    {
        return toString(data);
    }

    public static String toString(ByteBuffer data)
    {
        if (data == null || ! data.hasRemaining()) {
            return "";
        }
        // Dump message as text or hex-encoded string
        boolean isText = true;
        data.mark();
        while (data.hasRemaining()) {
            byte aData = data.get();
            if (aData < 32 || aData > 127) {
                isText = false;
                break;
            }
        }
        data.reset();
        String content;
        if (isText) {
            content = ZMQ.CHARSET.decode(data).toString();
        }
        else {
            content =  strhex(data);
        }
        data.reset();
        return content;
    }

    public static String toString(byte[] data)
    {
        return toString(ByteBuffer.wrap(data));
    }

    /**
     * @return data as a printable hex string
     */
    public String strhex()
    {
        return strhex(data);
    }

    /**
     * @return data as a printable hex string
     */
    public static String strhex(ByteBuffer data)
    {
        if (data == null || ! data.hasRemaining()) {
            return "";
        }
        StringBuilder b = new StringBuilder(data.limit() * 2);
        ByteBuffer readBuffer = data.asReadOnlyBuffer();
        while (readBuffer.hasRemaining()) {
            byte aData = readBuffer.get();
            int b1 = aData >>> 4 & 0xf;
            int b2 = aData & 0xf;
            b.append(HEX_CHAR.charAt(b1));
            b.append(HEX_CHAR.charAt(b2));
        }
        return b.toString();
    }

    public static String strhex(byte[] data)
    {
        return strhex(ByteBuffer.wrap(data));
    }

    public void print(PrintStream out, String prefix)
    {
        print(out, prefix, data);
    }

    public static void print(PrintStream out, String prefix, ByteBuffer data)
    {
        if (data == null) {
            return;
        }
        if (prefix != null) {
            out.printf("%s", prefix);
        }
        int size = data.remaining();
        out.printf("[%03d] ", size);
        boolean isText = true;
        ByteBuffer readBuffer = data.asReadOnlyBuffer();
        readBuffer.mark();
        while (readBuffer.hasRemaining()) {
            byte aData = readBuffer.get();
            if (aData < 32 || aData > 127) {
                isText = false;
                break;
            }
        }
        readBuffer.reset();
        readBuffer.rewind();
        int maxSize = isText ? 70 : 35;
        String elipsis = "";
        if (size > maxSize) {
            readBuffer.limit(maxSize);
            elipsis = "...";
        }
        if (isText) {
            out.append(ZMQ.CHARSET.decode(readBuffer));
        }
        else {
            while (readBuffer.hasRemaining()) {
                out.printf("%02X", readBuffer.get());
            }
        }
        out.printf("%s\n", elipsis);
        out.flush();
    }

    public static void print(PrintStream out, String prefix, byte[] data, int size)
    {
        print(out, prefix, ByteBuffer.wrap(data, 0, size));
    }
}
