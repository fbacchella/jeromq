package org.zeromq;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zeromq.Curve.KeyPair;

import zmq.io.mechanism.curve.CurveMechanismSettings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSocketConfigurator
{
    @Test
    void testBuild()
    {
        SocketConfigurator config = SocketConfigurator.build();
        assertNotNull(config);
        assertNull(config.endpoint);
        assertNull(config.sendHwm);
        assertNull(config.recvHwm);
        assertNull(config.maxMsgSize);
        assertNull(config.linger);
        assertNull(config.backlog);
    }

    @Test
    void testBuilder()
    {
        KeyPair kp = Curve.generateKeyPair();
        SocketConfigurator config = SocketConfigurator.builder()
                .endpoint("tcp://localhost:5555")
                .type(SocketType.SUB)
                .method(Method.BIND)
                .sendHwm(100)
                .recvHwm(200)
                .maxMsgSize(1024L)
                .linger(10)
                .curvePeerPublicKey(CurveMechanismSettings.curveKey(kp.publicKey))
                .curveSecretKey(CurveMechanismSettings.curveKey(kp.secretKey))
                .curvePublicKey(CurveMechanismSettings.curveKey(kp.publicKey))
                .backlog(50)
                .affinity(1L)
                .identity(new byte[]{1, 2, 3})
                .ipv6(true)
                .receiveBufferSize(4096)
                .sendBufferSize(8192)
                .receiveTimeOut(500)
                .reconnectIVL(1000)
                .reconnectIVLMax(2000)
                .sendTimeOut(300)
                .tcpKeepAlive(1)
                .tcpKeepAliveCount(5)
                .tcpKeepAliveIdle(60)
                .tcpKeepAliveInterval(30)
                .xpubVerbose(true)
                .tos(128)
                .heartbeatIvl(1000)
                .heartbeatTimeout(2000)
                .heartbeatTtl(3000)
                .heartbeatContext("ping".getBytes())
                .handshakeIvl(500)
                .socksProxyPort(1080)
                .socksProxyHost("proxy-host")
                .xpubNoDrop(true)
                .xpubManual(true)
                .xpubVerboser(true)
                .build();

        assertConfigurator(config);
    }

    @Test
    void testFromMap()
    {
        KeyPair kp = Curve.generateKeyPair();
        java.util.Map<String, Object> settings = new java.util.HashMap<>();
        settings.put("endpoint", "tcp://localhost:5555");
        settings.put("type", "SUB");
        settings.put("method", "BIND");
        settings.put("sendHwm", 100);
        settings.put("recvHwm", 200);
        settings.put("maxMsgSize", 1024L);
        settings.put("linger", 10);
        settings.put("curvePeerPublicKey", kp.publicKey);
        settings.put("curveSecretKey", kp.secretKey);
        settings.put("curvePublicKey", kp.publicKey);
        settings.put("backlog", 50);
        settings.put("affinity", 1L);
        settings.put("identity", new byte[]{1, 2, 3});
        settings.put("ipv6", true);
        settings.put("receiveBufferSize", 4096L);
        settings.put("sendBufferSize", 8192L);
        settings.put("receiveTimeOut", 500);
        settings.put("reconnectIVL", 1000L);
        settings.put("reconnectIVLMax", 2000L);
        settings.put("sendTimeOut", 300);
        settings.put("tcpKeepAlive", 1);
        settings.put("tcpKeepAliveCount", 5L);
        settings.put("tcpKeepAliveIdle", 60L);
        settings.put("tcpKeepAliveInterval", 30L);
        settings.put("xpubVerbose", true);
        settings.put("tos", 128);
        settings.put("heartbeatIvl", 1000L);
        settings.put("heartbeatTimeout", 2000L);
        settings.put("heartbeatTtl", 3000L);
        settings.put("heartbeatContext", "ping".getBytes());
        settings.put("handshakeIvl", 500L);
        settings.put("socksProxyPort", 1080);
        settings.put("socksProxyHost", "proxy-host");
        settings.put("xpubNoDrop", true);
        settings.put("xpubManual", true);
        settings.put("xpubVerboser", true);

        SocketConfigurator config = SocketConfigurator.from(settings);

        assertConfigurator(config);
    }

    private void assertConfigurator(SocketConfigurator config)
    {
        assertNotNull(config);
        assertEquals("tcp://localhost:5555", config.endpoint);
        assertEquals(SocketType.SUB, config.type);
        assertEquals(Method.BIND, config.method);
        assertEquals(100, config.sendHwm);
        assertEquals(200, config.recvHwm);
        assertEquals(1024L, config.maxMsgSize);
        assertEquals(10, config.linger);
        assertEquals(50, config.backlog);
        assertEquals(1L, config.affinity);
        assertNotNull(config.identity);
        assertEquals((byte) 1, config.identity.get(0));
        assertTrue(config.ipv6);
        assertEquals(4096, config.receiveBufferSize);
        assertEquals(8192, config.sendBufferSize);
        assertEquals(500, config.receiveTimeOut);
        assertEquals(1000, config.reconnectIVL);
        assertEquals(2000, config.reconnectIVLMax);
        assertEquals(300, config.sendTimeOut);
        assertEquals(1, config.tcpKeepAlive);
        assertEquals(5, config.tcpKeepAliveCount);
        assertEquals(60, config.tcpKeepAliveIdle);
        assertEquals(30, config.tcpKeepAliveInterval);
        assertTrue(config.xpubVerbose);
        assertEquals(128, config.tos);
        assertEquals(1000, config.heartbeatIvl);
        assertEquals(2000, config.heartbeatTimeout);
        assertEquals(3000, config.heartbeatTtl);
        assertNotNull(config.heartbeatContext);
        byte[] hbBytes = new byte[config.heartbeatContext.remaining()];
        config.heartbeatContext.duplicate().get(hbBytes);
        assertEquals("ping", new String(hbBytes));
        assertEquals(500, config.handshakeIvl);
        assertEquals(1080, config.socksProxyPort);
        assertEquals("proxy-host", config.socksProxyHost);
        assertTrue(config.xpubNoDrop);
        assertTrue(config.xpubManual);
        assertTrue(config.xpubVerboser);
    }

    @Test
    void testGetSocket()
    {
        byte[] customIdentity = "custom-id".getBytes();
        SocketConfigurator config = SocketConfigurator.builder()
                .endpoint("inproc://test")
                .type(SocketType.XPUB)
                .method(Method.BIND)
                .maxMsgSize(-1L)
                .linger(0)
                .backlog(0)
                .affinity(0L)
                .tcpKeepAlive(0)
                .tcpKeepAliveCount(0)
                .tcpKeepAliveIdle(0)
                .recvHwm(0)
                .sendHwm(0)
                .tos(0)
                .sendBufferSize(0)
                .receiveBufferSize(0)
                .sendTimeOut(0)
                .receiveTimeOut(0)
                .reconnectIVL(0)
                .reconnectIVLMax(0)
                .tcpKeepAliveInterval(0)
                .heartbeatIvl(0)
                .heartbeatTimeout(0)
                .heartbeatTtl(0)
                .handshakeIvl(0)
                .xpubVerbose(false)
                .xpubNoDrop(false)
                .xpubManual(false)
                .xpubVerboser(false)
                .ipv6(false)
                .identity(customIdentity)
                .build();

        try (ZContext ctx = new ZContext()) {
            ZMQ.Socket socket = ctx.createSocket(config.type);
            config.getSocket(socket);
            assertNotNull(socket);
            assertEquals(0, socket.getLinger());
            assertEquals(SocketType.XPUB, socket.getSocketType());
            assertArrayEquals(customIdentity, socket.getIdentity());
        }
    }

    @Test
    void testIdentityOverwritten()
    {
        try (ZContext ctx = new ZContext()) {
            SocketConfigurator config = SocketConfigurator.builder()
                    .endpoint("inproc://test")
                    .type(SocketType.PUB)
                    .method(Method.BIND)
                    .maxMsgSize(-1L)
                    .linger(0)
                    .backlog(0)
                    .affinity(0L)
                    .tcpKeepAlive(0)
                    .tcpKeepAliveCount(0)
                    .tcpKeepAliveIdle(0)
                    .recvHwm(0)
                    .sendHwm(0)
                    .tos(0)
                    .sendBufferSize(0)
                    .receiveBufferSize(0)
                    .sendTimeOut(0)
                    .receiveTimeOut(0)
                    .reconnectIVL(0)
                    .reconnectIVLMax(0)
                    .tcpKeepAliveInterval(0)
                    .heartbeatIvl(0)
                    .heartbeatTimeout(0)
                    .heartbeatTtl(0)
                    .handshakeIvl(0)
                    .xpubVerbose(false)
                    .xpubNoDrop(false)
                    .xpubManual(false)
                    .xpubVerboser(false)
                    .ipv6(false)
                    .build();

            ZMQ.Socket socket = ctx.createSocket(config.type);
            config.getSocket(socket);
            // Par défaut, Configurator génère une identité basée sur l'URL
            String url = "inproc://test:PUB:O";
            assertArrayEquals(url.getBytes(), socket.getIdentity());
            socket.close();
        }
    }

    @Test
    void testCustomIdentity()
    {
        byte[] customId = "my-custom-id".getBytes();
        try (ZContext ctx = new ZContext()) {
            SocketConfigurator config = SocketConfigurator.builder()
                    .endpoint("inproc://test")
                    .type(SocketType.PUB)
                    .method(Method.BIND)
                    .maxMsgSize(-1L)
                    .linger(0)
                    .backlog(0)
                    .affinity(0L)
                    .tcpKeepAlive(0)
                    .tcpKeepAliveCount(0)
                    .tcpKeepAliveIdle(0)
                    .recvHwm(0)
                    .sendHwm(0)
                    .tos(0)
                    .sendBufferSize(0)
                    .receiveBufferSize(0)
                    .sendTimeOut(0)
                    .receiveTimeOut(0)
                    .reconnectIVL(0)
                    .reconnectIVLMax(0)
                    .tcpKeepAliveInterval(0)
                    .heartbeatIvl(0)
                    .heartbeatTimeout(0)
                    .heartbeatTtl(0)
                    .handshakeIvl(0)
                    .xpubVerbose(false)
                    .xpubNoDrop(false)
                    .xpubManual(false)
                    .xpubVerboser(false)
                    .ipv6(false)
                    .identity(customId)
                    .build();

            ZMQ.Socket socket = ctx.createSocket(config.type);
            config.getSocket(socket);
            assertArrayEquals(customId, socket.getIdentity());
            socket.close();
        }
    }

    @Test
    void testFromMapWithByteBuffer()
    {
        Map<String, Object> settings = new HashMap<>();
        byte[] identityBytes = new byte[]{1, 2, 3};
        byte[] contextBytes = "hb-context".getBytes();

        settings.put("identity", ByteBuffer.wrap(identityBytes));
        settings.put("heartbeatContext", ByteBuffer.wrap(contextBytes));

        SocketConfigurator config = SocketConfigurator.from(settings);

        byte[] actualIdentity = new byte[config.identity.remaining()];
        config.identity.duplicate().get(actualIdentity);
        assertArrayEquals(identityBytes, actualIdentity);
        byte[] actualContext = new byte[config.heartbeatContext.remaining()];
        config.heartbeatContext.duplicate().get(actualContext);
        assertArrayEquals(contextBytes, actualContext);
    }

    @Test
    void testFromMapWithReadOnlyByteBuffer()
    {
        Map<String, Object> settings = new HashMap<>();
        byte[] identityBytes = new byte[]{4, 5, 6};
        ByteBuffer identityBB = ByteBuffer.wrap(identityBytes).asReadOnlyBuffer();

        settings.put("identity", identityBB);

        SocketConfigurator config = SocketConfigurator.from(settings);

        byte[] actualIdentity = new byte[config.identity.remaining()];
        config.identity.duplicate().get(actualIdentity);
        assertArrayEquals(identityBytes, actualIdentity);
    }

    @Test
    void testFromMapInvalidValueIncludesKey()
    {
        Map<String, Object> settings = new HashMap<>();
        settings.put("sendHwm", "not-a-number");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SocketConfigurator.from(settings));
        assertTrue(ex.getMessage().contains("sendHwm"),
                "Exception message should contain the key name 'sendHwm'");
    }

    @Test
    void testDefensiveCopies()
    {
        byte[] identity = new byte[]{1, 2, 3};
        byte[] heartbeatContext = new byte[]{4, 5, 6};

        SocketConfigurator.Builder builder = SocketConfigurator.builder()
                .identity(identity)
                .heartbeatContext(heartbeatContext);

       SocketConfigurator config = builder.build();

        // Modify original arrays
        identity[0] = 9;
        heartbeatContext[0] = 9;

        // Check that Configurator has the original values (defensive copy in Builder)
        byte[] actualIdentity = new byte[config.identity.remaining()];
        config.identity.duplicate().get(actualIdentity);
        assertArrayEquals(new byte[]{1, 2, 3}, actualIdentity);
        byte[] actualContext = new byte[config.heartbeatContext.remaining()];
        config.heartbeatContext.duplicate().get(actualContext);
        assertArrayEquals(new byte[]{4, 5, 6}, actualContext);

        // Modify original arrays again after build
        identity[1] = 8;
        heartbeatContext[1] = 8;

        // Check that Configurator is still isolated
        byte[] actualIdentity2 = new byte[config.identity.remaining()];
        config.identity.duplicate().get(actualIdentity2);
        assertArrayEquals(new byte[]{1, 2, 3}, actualIdentity2);
        byte[] actualContext2 = new byte[config.heartbeatContext.remaining()];
        config.heartbeatContext.duplicate().get(actualContext2);
        assertArrayEquals(new byte[]{4, 5, 6}, actualContext2);
    }
}
