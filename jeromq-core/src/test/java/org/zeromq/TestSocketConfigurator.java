package org.zeromq;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSocketConfigurator {

    @Test
    void testBuild() {
        SocketConfigurator config = SocketConfigurator.build();
        assertNotNull(config);
        assertEquals(SocketConfigurator.DEFAULT_TYPE, config.type);
        assertEquals(SocketConfigurator.DEFAULT_METHOD, config.method);
        assertNull(config.endpoint);
        assertEquals(zmq.ZMQ.DEFAULT_SEND_HWM, config.sendHwm);
        assertEquals(zmq.ZMQ.DEFAULT_RECV_HWM, config.recvHwm);
        assertEquals(zmq.ZMQ.DEFAULT_MAX_MSG_SIZE, config.maxMsgSize);
        assertEquals(zmq.ZMQ.DEFAULT_LINGER, config.linger);
        assertEquals(zmq.ZMQ.DEFAULT_BACKLOG, config.backlog);
    }

    @Test
    void testBuilder() {
        SocketConfigurator config = SocketConfigurator.builder()
                .endpoint("tcp://localhost:5555")
                .type(SocketType.SUB)
                .method(Method.BIND)
                .sendHwm(100)
                .recvHwm(200)
                .maxMsgSize(1024L)
                .linger(10)
                .peerPublicKey("peer-key")
                .privateKeyFile("private-key-file")
                .publicKey("public-key")
                .autoCreate(true)
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
    void testFromMap() {
        java.util.Map<String, Object> settings = new java.util.HashMap<>();
        settings.put("endpoint", "tcp://localhost:5555");
        settings.put("type", "SUB");
        settings.put("method", "BIND");
        settings.put("sendHwm", 100);
        settings.put("recvHwm", 200);
        settings.put("maxMsgSize", 1024L);
        settings.put("linger", 10);
        settings.put("peerPublicKey", "peer-key");
        settings.put("privateKeyFile", "private-key-file");
        settings.put("publicKey", "public-key");
        settings.put("autoCreate", true);
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

    private void assertConfigurator(SocketConfigurator config) {
        assertNotNull(config);
        assertEquals("tcp://localhost:5555", config.endpoint);
        assertEquals(SocketType.SUB, config.type);
        assertEquals(Method.BIND, config.method);
        assertEquals(100, config.sendHwm);
        assertEquals(200, config.recvHwm);
        assertEquals(1024L, config.maxMsgSize);
        assertEquals(10, config.linger);
        assertEquals("peer-key", config.peerPublicKey);
        assertEquals("private-key-file", config.privateKeyFile);
        assertEquals("public-key", config.publicKey);
        assertTrue(config.autoCreate);
        assertEquals(50, config.backlog);
        assertEquals(1L, config.affinity);
        assertNotNull(config.identity);
        assertEquals((byte) 1, config.identity[0]);
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
        assertEquals("ping", new String(config.heartbeatContext));
        assertEquals(500, config.handshakeIvl);
        assertEquals(1080, config.socksProxyPort);
        assertEquals("proxy-host", config.socksProxyHost);
        assertTrue(config.xpubNoDrop);
        assertTrue(config.xpubManual);
        assertTrue(config.xpubVerboser);
    }

    @Test
    public void testGetSocket() {
        byte[] customIdentity = "custom-id".getBytes();
        SocketConfigurator config = SocketConfigurator.builder()
                .endpoint("inproc://test")
                .type(SocketType.XPUB)
                .method(Method.BIND)
                .linger(0)
                .identity(customIdentity)
                .build();

        try (ZContext ctx = new ZContext()) {
            ZMQ.Socket socket = config.getSocket(ctx);
            assertNotNull(socket);
            assertEquals(0, socket.getLinger());
            assertEquals(SocketType.XPUB, socket.getSocketType());
            assertArrayEquals(customIdentity, socket.getIdentity());
            socket.close();
        }
    }

    @Test
    public void testIdentityOverwritten() {
        try (ZContext ctx = new ZContext()) {
            SocketConfigurator config = SocketConfigurator.builder()
                    .endpoint("inproc://test")
                    .type(SocketType.PUB)
                    .method(Method.BIND)
                    .build();

            ZMQ.Socket socket = config.getSocket(ctx);
            // Par défaut, Configurator génère une identité basée sur l'URL
            String url = "inproc://test:PUB:O";
            assertArrayEquals(url.getBytes(), socket.getIdentity());
            socket.close();
        }
    }

    @Test
    public void testCustomIdentity() {
        byte[] customId = "my-custom-id".getBytes();
        try (ZContext ctx = new ZContext()) {
            SocketConfigurator config = SocketConfigurator.builder()
                    .endpoint("inproc://test")
                    .type(SocketType.PUB)
                    .method(Method.BIND)
                    .identity(customId)
                    .build();

            ZMQ.Socket socket = config.getSocket(ctx);
            // L'identité personnalisée devrait être respectée
            assertArrayEquals(customId, socket.getIdentity());
            socket.close();
        }
    }

    @Test
    public void testFromMapWithByteBuffer() {
        Map<String, Object> settings = new HashMap<>();
        byte[] identityBytes = new byte[]{1, 2, 3};
        byte[] contextBytes = "hb-context".getBytes();

        settings.put("identity", ByteBuffer.wrap(identityBytes));
        settings.put("heartbeatContext", ByteBuffer.wrap(contextBytes));

        SocketConfigurator config = SocketConfigurator.from(settings);

        assertArrayEquals(identityBytes, config.identity);
        assertArrayEquals(contextBytes, config.heartbeatContext);
    }

    @Test
    public void testFromMapWithReadOnlyByteBuffer() {
        Map<String, Object> settings = new HashMap<>();
        byte[] identityBytes = new byte[]{4, 5, 6};
        ByteBuffer identityBB = ByteBuffer.wrap(identityBytes).asReadOnlyBuffer();

        settings.put("identity", identityBB);

        SocketConfigurator config = SocketConfigurator.from(settings);

        assertArrayEquals(identityBytes, config.identity);
    }

    @Test
    public void testDefensiveCopies() {
        byte[] identity = new byte[]{1, 2, 3};
        byte[] heartbeatContext = new byte[]{4, 5, 6};

        SocketConfigurator.Builder builder = SocketConfigurator.builder()
                .identity(identity)
                .heartbeatContext(heartbeatContext);

        // Modify original arrays
        identity[0] = 9;
        heartbeatContext[0] = 9;

        SocketConfigurator config = builder.build();

        // Check that Configurator has the original values (defensive copy in Builder)
        assertArrayEquals(new byte[]{1, 2, 3}, config.identity);
        assertArrayEquals(new byte[]{4, 5, 6}, config.heartbeatContext);

        // Modify original arrays again after build
        identity[1] = 8;
        heartbeatContext[1] = 8;

        // Check that Configurator is still isolated
        assertArrayEquals(new byte[]{1, 2, 3}, config.identity);
        assertArrayEquals(new byte[]{4, 5, 6}, config.heartbeatContext);
    }
}
