package org.zeromq;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import zmq.util.Z85;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncodingDetectorTest
{
    @Test
    void testDecodeBase64()
    {
        // Purely alphanumeric, length 4 (multiple of 4, not of 5) => detected as BASE64
        String encoded = "AAAA";
        byte[] expected = Base64.getDecoder().decode(encoded);
        assertArrayEquals(expected, EncodingDetector.decode(encoded));
    }

    @Test
    void testDecodeBase64Url()
    {
        // Force a URL-safe Base64 string with '-' or '_'
        byte[] original = { (byte) 0xFB, (byte) 0xFF, (byte) 0xFE };
        String encoded = Base64.getUrlEncoder().encodeToString(original);
        assertArrayEquals(original, EncodingDetector.decode(encoded));
    }

    @Test
    void testDecodeZ85()
    {
        byte[] original = { 0x01, 0x02, 0x03, 0x04 };
        String encoded = Z85.encode(original, original.length);
        assertArrayEquals(original, EncodingDetector.decode(encoded));
    }

    @Test
    void testDecodeUnknownThrows()
    {
        assertThrows(IllegalArgumentException.class, () -> EncodingDetector.decode("!!!"));
    }

    @Test
    void testDecodeAmbiguousThrows()
    {
        // Purely alphanumeric, length multiple of 20 => AMBIGUOUS
        String ambiguous = "AAAAAAAAAAAAAAAAAAAA"; // 20 chars
        assertThrows(IllegalArgumentException.class, () -> EncodingDetector.decode(ambiguous));
    }

    @Test
    void testDecodeNullThrows()
    {
        assertThrows(IllegalArgumentException.class, () -> EncodingDetector.decode(null));
    }
    @Test
    void testDecodeEmpty()
    {
        String encoded = "";
        byte[] expected = new byte[0];
        assertArrayEquals(expected, EncodingDetector.decode(encoded));
    }
}
