package zmq.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import zmq.ZMQ;

class V0ProtocolTest extends AbstractProtocolVersion
{
    private static final Logger logger = LogManager.getLogger(V0ProtocolTest.class);

    @Test
    void testFixIssue524() throws IOException, InterruptedException
    {
        for (int idx = 0; idx < REPETITIONS; ++idx) {
            testProtocolVersion0short();
        }
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testProtocolVersion0short() throws IOException, InterruptedException
    {
        ByteBuffer raw = ByteBuffer.allocate(11)
                // send unversioned identity message
                // size
                .put((byte) 0x01)
                // flags
                .put((byte) 0);
        // and payload
        raw.put((byte) 8).put((byte) 0).put("abcdefg".getBytes(ZMQ.CHARSET));
        assertProtocolVersion(0, raw, "abcdefg");
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testProtocolVersion0long() throws IOException, InterruptedException
    {
        ByteBuffer raw = ByteBuffer.allocate(35)
                // send unversioned identity message
                // large message indicator
                .put((byte) 0xff)
                // size
                .put(new byte[7]).put((byte) 9)
                // flags
                .put((byte) 0)
                // identity
                .put("identity".getBytes(ZMQ.CHARSET));

        // and payload
        raw.put((byte) 0xff).put(new byte[7]).put((byte) 8).put((byte) 0).put("abcdefg".getBytes(ZMQ.CHARSET));
        assertProtocolVersion(0, raw, "abcdefg");
    }

    private byte[] assertProtocolVersion(int version, ByteBuffer raw, String payload)
            throws IOException, InterruptedException
    {
        return assertProtocolVersion(version, Collections.singletonList(raw), payload);
    }
}
