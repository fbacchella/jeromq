package zmq.io.mechanism.curve;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static zmq.io.mechanism.curve.Curve.CURVE_KEYSIZE;

class CurveMechanismSettingsTest
{
    @Test
    void testCurveKeyByteArray()
    {
        byte[] key = new byte[CURVE_KEYSIZE];
        Arrays.fill(key, (byte) 7);
        byte[] result = CurveMechanismSettings.curveKey(key);
        assertArrayEquals(key, result);
    }

    @Test
    void testCurveKeyByteBufferHeap()
    {
        byte[] key = new byte[CURVE_KEYSIZE];
        Arrays.fill(key, (byte) 42);
        ByteBuffer buf = ByteBuffer.wrap(key);
        byte[] result = CurveMechanismSettings.curveKey(buf);
        assertArrayEquals(key, result);
    }

    @Test
    void testCurveKeyByteBufferDirect()
    {
        byte[] key = new byte[CURVE_KEYSIZE];
        Arrays.fill(key, (byte) 13);
        ByteBuffer buf = ByteBuffer.allocateDirect(CURVE_KEYSIZE);
        buf.put(key);
        buf.flip();
        byte[] result = CurveMechanismSettings.curveKey(buf);
        assertArrayEquals(key, result);
    }

    @Test
    void testCurveKeyByteBufferDoesNotConsumeOriginal()
    {
        byte[] key = new byte[CURVE_KEYSIZE];
        Arrays.fill(key, (byte) 5);
        ByteBuffer buf = ByteBuffer.wrap(key);
        CurveMechanismSettings.curveKey(buf);
        // original buffer position must be unchanged
        assertEquals(0, buf.position());
    }

    @Test
    void testCurveKeyByteBufferWrongSize()
    {
        ByteBuffer key = ByteBuffer.allocate(CURVE_KEYSIZE - 1);
        assertThrows(IllegalArgumentException.class, () -> CurveMechanismSettings.curveKey(key));
    }

    @Test
    void testCurveKeyByteArrayWrongSize()
    {
        byte[] key = new byte[CURVE_KEYSIZE - 1];
        assertThrows(IllegalArgumentException.class, () -> CurveMechanismSettings.curveKey(key));
    }

    @Test
    void testCurveKeyStringWrongLength()
    {
        assertThrows(IllegalArgumentException.class, () -> CurveMechanismSettings.curveKey("tooshort"));
    }

    @Test
    void testCurveKeyUnexpectedType()
    {
        assertThrows(IllegalArgumentException.class, () -> CurveMechanismSettings.curveKey(42));
    }

    @Test
    void testCurveKeyNull()
    {
        assertThrows(IllegalArgumentException.class, () -> CurveMechanismSettings.curveKey(null));
    }
}
