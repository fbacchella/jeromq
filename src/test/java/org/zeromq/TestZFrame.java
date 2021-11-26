package org.zeromq;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;

import org.junit.Test;

import zmq.util.function.Function;

public class TestZFrame
{
    private Function<Integer, ByteBuffer> allocator;

    public TestZFrame()
    {
        this(ByteBuffer::allocateDirect);
    }

    private TestZFrame(Function<Integer, ByteBuffer> allocator)
    {
        this.allocator = allocator;
    }

    @Test
    public void shouldWorkForFlippedBuffers()
    {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put((byte) 'a');
        buffer.put((byte) 'b');
        buffer.put((byte) 'c');
        buffer.flip();
        System.out.println(buffer);
        ZFrame msg = new ZFrame(buffer);
        assertThat(msg.getData(), is(new byte[] {'a', 'b', 'c'}));
    }

    @Test
    public void testPutString()
    {
        ZFrame msg = initMsg();

        msg.reset("data");
        byte[] dst = msg.getData();
        assertThat(dst, is(new byte[] {'d', 'a', 't', 'a' }));
    }

    private ZFrame initMsg()
    {
        return initDirectMsg(allocator);
    }

    private ZFrame initDirectMsg(Function<Integer, ByteBuffer> allocator)
    {
        int size = 30;
        ByteBuffer buffer = allocator.apply(size);
        for (int idx = 0; idx < size; ++idx) {
            buffer.put((byte) idx);
        }
        buffer.position(0);
        return new ZFrame(buffer);
    }

    // Check that ZFrame#getData() is correct when the backing array has an offset.
    @Test
    public void testDataNonZeroOffset()
    {
        byte[] data = new byte[]{10, 11, 12};

        ByteBuffer buffer = ByteBuffer.wrap(data, 1, 2).slice();
        ZFrame msg = new ZFrame(buffer);

        assertThat(msg.getData(), is(new byte[]{11, 12}));
    }

    // Check that data returned by ZFrame#getData() is correct when the end of
    // the backing array is not used by the buffer.
    @Test
    public void testGetBytesArrayExtendsFurther()
    {
        byte[] data = new byte[]{10, 11, 12};

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, 2).slice();
        ZFrame msg = new ZFrame(buffer);

        assertThat(msg.getData(), is(new byte[]{10, 11}));
    }

    // Check that ZFrame#data() doesn't make unnecessary copies.
    @Test
    public void testDataNoCopy()
    {
        byte[] data = new byte[]{10, 11, 12};

        ZFrame msg = new ZFrame(data);

        assertThat(msg.getData(), sameInstance(data));
    }

    // Check that String encoding is respected.
    @Test
    public void testString()
    {
         ZFrame msg = new ZFrame("œuf");

        assertThat(msg.getString(ZMQ.CHARSET), is("œuf"));
    }

    // Check that null data are working
    @Test
    public void testNull()
    {
        ZFrame msg = new ZFrame((String) null);
        assertThat(msg.getString(ZMQ.CHARSET), is(""));
        assertThat(msg.duplicate().getData(), nullValue());
        assertThat(msg.getData(), nullValue());
        assertThat(msg.getDataBuffer(), nullValue());
        assertThat(msg.hasSameData(msg), is(true));
        assertThat(msg.hasSameData(null), is(false));
        msg.size();
    }
}
