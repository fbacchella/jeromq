package zmq.util;

import java.nio.ByteBuffer;

import zmq.Msg;
import zmq.ZMQ;

public class Blob
{
    private final ByteBuffer buf;

    public Blob(byte[] data)
    {
        buf = ByteBuffer.wrap(data);
    }

    public Blob(ByteBuffer data)
    {
        buf = data.duplicate();
    }

    public Blob(Msg msg)
    {
        buf = msg.buf();
    }

    public Blob(String str)
    {
        buf = ZMQ.CHARSET.encode(str);
    }

    /**
     * @deprecated use the constructor directly
     * @param msg
     * @return
     */
    @Deprecated
    public static Blob createBlob(Msg msg)
    {
        return new Blob(msg.buf());
    }

    /**
     * @deprecated use the constructor directly
     * @param data
     * @return
     */
    @Deprecated
    public static Blob createBlob(byte[] data)
    {
        return new Blob(ByteBuffer.wrap(data));
    }

    /**
     * @deprecated use the constructor directly
     * @param data
     * @return
     */
    @Deprecated
    public static Blob createBlob(String data)
    {
        return new Blob(ZMQ.CHARSET.encode(data));
    }

    public int size()
    {
        return buf.remaining();
    }

    public byte[] data()
    {
        if (buf.hasArray() && buf.arrayOffset() == 0 && buf.array().length == buf.limit()) {
            // If the backing array is exactly the buffer content, return it without copy.
            return buf.array();
        }
        else {
            // No backing array -> use ByteBuffer#get().
            byte[] array = new byte[buf.remaining()];
            buf.mark();
            buf.get(array);
            buf.reset();
            return array;
        }
    }

    public ByteBuffer buf()
    {
        return buf.duplicate();
    }

    @Override
    public boolean equals(Object t)
    {
        if (t instanceof Blob) {
            return buf.equals(((Blob) t).buf);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return buf.hashCode();
    }
}
