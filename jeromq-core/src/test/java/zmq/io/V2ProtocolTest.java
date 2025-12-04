package zmq.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.Test;

import zmq.ZMQ;
import zmq.util.Wire;

class V2ProtocolTest extends AbstractProtocolVersion
{
    @Override
    protected ByteBuffer identity()
    {
        return ByteBuffer.allocate(2)
                // flag
                .put((byte) 0)
                // length
                .put((byte) 0);
    }

    @Test
    void testFixIssue524() throws IOException, InterruptedException
    {
        for (int idx = 0; idx < REPETITIONS; ++idx) {
            testProtocolVersion2short();
        }
    }

    @Test
    void testProtocolVersion2short() throws IOException, InterruptedException
    {
        List<ByteBuffer> raws = raws(1);
        raws.add(identity());

        ByteBuffer raw = ByteBuffer.allocate(9)
                // flag
                .put((byte) 0)
                // length
                .put((byte) 7)
                // payload
                .put("abcdefg".getBytes(ZMQ.CHARSET));
        raws.add(raw);
        assertProtocolVersion(2, raws, "abcdefg");
    }

    @Test
    void testProtocolVersion2long() throws IOException, InterruptedException
    {
        List<ByteBuffer> raws = raws(1);
        raws.add(identity());

        ByteBuffer raw = ByteBuffer.allocate(17);
        // flag
        raw.put((byte) 2);
        // length
        Wire.putUInt64(raw, 8);
        // payload
        raw.put("abcdefgh".getBytes(ZMQ.CHARSET));
        raws.add(raw);
        assertProtocolVersion(2, raws, "abcdefgh");
    }
}
