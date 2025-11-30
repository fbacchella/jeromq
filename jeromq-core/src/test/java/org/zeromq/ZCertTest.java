package org.zeromq;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeromq.util.ZMetadata;

import zmq.util.AndroidProblematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AndroidProblematic
public class ZCertTest
{
    @TempDir
    private Path CERTSTORE_LOCATION;

    @Test
    void testConstructorNullStringPublicKey()
    {
        assertThrows(IllegalArgumentException.class, () -> new ZCert((String) null, null));
    }

    @Test
    void testConstructorNullBytesPublicKey()
    {
        assertThrows(IllegalArgumentException.class, () -> new ZCert((byte[]) null, null));
    }

    @Test
    void testConstructorInvalidBytesPublicKey()
    {
        assertThrows(IllegalArgumentException.class, () -> new ZCert(new byte[0], null));
    }

    @Test
    void testConstructorInvalidStringPublicKey()
    {
        assertThrows(IllegalArgumentException.class, () -> new ZCert("", null));
    }

    @Test
    void testConstructorValidPublicKeyZ85()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        assertEquals(40, keyPair.publicKey.length());

        ZCert cert = new ZCert(keyPair.publicKey);

        assertEquals(keyPair.publicKey, cert.getPublicKeyAsZ85());
        assertArrayEquals(ZMQ.Curve.z85Decode(keyPair.publicKey), cert.getPublicKey());
        assertNull(cert.getSecretKeyAsZ85());
        assertNull(cert.getSecretKey());
    }

    @Test
    void testConstructorValidPublicKey()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        byte[] bytes = ZMQ.Curve.z85Decode(keyPair.publicKey);

        ZCert cert = new ZCert(bytes, null);

        assertEquals(keyPair.publicKey, cert.getPublicKeyAsZ85());
        assertArrayEquals(bytes, cert.getPublicKey());
        assertNull(cert.getSecretKeyAsZ85());
        assertNull(cert.getSecretKey());
    }

    @Test
    void testConstructorValidKeysZ85()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        assertEquals(40, keyPair.publicKey.length());

        ZCert cert = new ZCert(keyPair.publicKey, keyPair.secretKey);

        assertEquals(keyPair.publicKey, cert.getPublicKeyAsZ85());
        assertEquals(keyPair.secretKey, cert.getSecretKeyAsZ85());
        assertArrayEquals(ZMQ.Curve.z85Decode(keyPair.publicKey), cert.getPublicKey());
        assertArrayEquals(ZMQ.Curve.z85Decode(keyPair.secretKey), cert.getSecretKey());
    }

    @Test
    void testConstructorValidKeys()
    {
        ZMQ.Curve.KeyPair keyPair = ZMQ.Curve.generateKeyPair();
        byte[] bytes = ZMQ.Curve.z85Decode(keyPair.publicKey);
        byte[] secret = ZMQ.Curve.z85Decode(keyPair.secretKey);

        ZCert cert = new ZCert(bytes, secret);

        assertEquals(keyPair.publicKey, cert.getPublicKeyAsZ85());
        assertEquals(keyPair.secretKey, cert.getSecretKeyAsZ85());
        assertArrayEquals(bytes, cert.getPublicKey());
        assertArrayEquals(secret, cert.getSecretKey());
    }

    @Test
    void testSetMeta()
    {
        ZCert cert = new ZCert();

        cert.setMeta("version", "1");
        assertEquals("1", cert.getMeta("version"));

        cert.setMeta("version", "2");
        assertEquals("2", cert.getMeta("version"));
    }

    @Test
    void testGetMeta()
    {
        ZCert cert = new ZCert();

        cert.setMeta("version", "1");
        ZMetadata meta = cert.getMetadata();
        assertEquals("1", meta.get("version"));

        meta.set("version", "2");
        assertEquals("2", cert.getMeta("version"));
    }

    @Test
    void testUnsetMeta()
    {
        ZCert cert = new ZCert();

        cert.setMeta("version", "1");
        cert.unsetMeta("version");
        assertNull(cert.getMeta("version"));
    }

    @Test
    void testSavePublic() throws IOException
    {
        ZCert cert = new ZCert("uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U",
                     "!LeSNcjV%qv!apmqePOP:}MBWPCHfdY4IkqO=AW0");
        cert.setMeta("version", "1");

        StringWriter writer = new StringWriter();
        cert.savePublic(writer);

        String datePattern = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-]?[0-9]{4}";
        String expected = "# \\*\\* Generated on " + datePattern + " by ZCert \\*\\*\n" +
                                  "#    ZeroMQ CURVE Public Certificate\n" +
                                  "#    Exchange securely, or use a secure mechanism to verify the contents\n" +
                                  "#    of this file after exchange. Store public certificates in your home\n" +
                                  "#    directory, in the .curve subdirectory.\n\n" +
                                  "metadata\n" +
                                  "    version = \"1\"\n" +
                                  "curve\n" +
                                  "    public-key = \"uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U\"\n";
        String result = writer.toString();
        assertTrue(Pattern.compile(expected).matcher(result).matches());
    }

    @Test
    void testSaveSecret() throws IOException
    {
        ZCert cert = new ZCert("uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U",
                      "!LeSNcjV%qv!apmqePOP:}MBWPCHfdY4IkqO=AW0");
        cert.setMeta("version", "1");

        StringWriter writer = new StringWriter();
        cert.saveSecret(writer);

        String datePattern = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[+-]?[0-9]{4}";
        String expected = "# \\*\\* Generated on " + datePattern + " by ZCert \\*\\*\n" +
                                  "#    ZeroMQ CURVE \\*\\*Secret\\*\\* Certificate\n" +
                                  "#    DO NOT PROVIDE THIS FILE TO OTHER USERS nor change its permissions.\n\n" +
                                  "metadata\n" +
                                  "    version = \"1\"\n" +
                                  "curve\n" +
                                  "    secret-key = \"!LeSNcjV%qv!apmqePOP:}MBWPCHfdY4IkqO=AW0\"\n" +
                                  "    public-key = \"uYax]JF%mz@r%ERApd<h]pkJ/Wn//lG!%mQ>Ob3U\"\n";
        String result = writer.toString();
        assertTrue(Pattern.compile(expected).matcher(result).matches());
    }

    @Test
    void testSavePublicFile() throws IOException
    {
        ZCert cert = new ZCert();
        Path p = CERTSTORE_LOCATION.resolve("test.cert");
        cert.savePublic(p);
        assertTrue(Files.exists(p));
    }

    @Test
    void testSaveSecretFile() throws IOException
    {
        ZCert cert = new ZCert();
        Path p = CERTSTORE_LOCATION.resolve("test_secret.cert");
        cert.saveSecret(p);
        assertTrue(Files.exists(p));
    }
}
