package zmq.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZMQ;
import zmq.util.Utils;

class MetadataTest
{
    private static final Logger logger = LogManager.getLogger(MetadataTest.class);

    private static class ZapHandler implements Runnable
    {
        private final SocketBase handler;

        public ZapHandler(SocketBase handler)
        {
            this.handler = handler;
        }

        @SuppressWarnings("unused")
        @Override
        public void run()
        {
            byte[] metadata = { 5, 'H', 'e', 'l', 'l', 'o', 0, 0, 0, 5, 'W', 'o', 'r', 'l', 'd' };
            // Process ZAP requests forever
            while (true) {
                Msg version = ZMQ.recv(handler, 0);
                if (version == null) {
                    break; // Terminating
                }
                Msg sequence = ZMQ.recv(handler, 0);
                Msg domain = ZMQ.recv(handler, 0);
                Msg address = ZMQ.recv(handler, 0);
                Msg identity = ZMQ.recv(handler, 0);
                Msg mechanism = ZMQ.recv(handler, 0);

                Assertions.assertEquals("1.0", new String(version.data(), ZMQ.CHARSET));
                Assertions.assertEquals("NULL", new String(mechanism.data(), ZMQ.CHARSET));

                int ret = ZMQ.send(handler, version, ZMQ.ZMQ_SNDMORE);
                Assertions.assertEquals(3, ret);

                ret = ZMQ.send(handler, sequence, ZMQ.ZMQ_SNDMORE);
                Assertions.assertEquals(1, ret);

                logger.debug("Sending ZAP reply");
                if ("DOMAIN".equals(new String(domain.data(), ZMQ.CHARSET))) {
                    ret = ZMQ.send(handler, "200", ZMQ.ZMQ_SNDMORE);
                    Assertions.assertEquals(3, ret);
                    ret = ZMQ.send(handler, "OK", ZMQ.ZMQ_SNDMORE);
                    Assertions.assertEquals(2, ret);
                    ret = ZMQ.send(handler, "anonymous", ZMQ.ZMQ_SNDMORE);
                    Assertions.assertEquals(9, ret);
                    ret = ZMQ.send(handler, metadata, metadata.length, 0);
                    Assertions.assertEquals(metadata.length, ret);
                } else {
                    ret = ZMQ.send(handler, "400", ZMQ.ZMQ_SNDMORE);
                    Assertions.assertEquals(3, ret);
                    ret = ZMQ.send(handler, "BAD DOMAIN", ZMQ.ZMQ_SNDMORE);
                    Assertions.assertEquals(10, ret);
                    ret = ZMQ.send(handler, "", ZMQ.ZMQ_SNDMORE);
                    Assertions.assertEquals(0, ret);
                    ret = ZMQ.send(handler, "", 0);
                    Assertions.assertEquals(0, ret);
                }
            }
            ZMQ.closeZeroLinger(handler);
        }
    }

    @Test
    void testMetadata() throws IOException, InterruptedException
    {
        int port = Utils.findOpenPort();
        String host = "tcp://127.0.0.1:" + port;
        Ctx ctx = ZMQ.createContext();

        // Spawn ZAP handler
        // We create and bind ZAP socket in main thread to avoid case
        // where child thread does not start up fast enough.
        SocketBase handler = ZMQ.socket(ctx, ZMQ.ZMQ_REP);
        Assertions.assertNotNull(handler);
        boolean rc = ZMQ.bind(handler, "inproc://zeromq.zap.01");
        Assertions.assertTrue(rc);

        Thread thread = new Thread(new ZapHandler(handler));
        thread.start();

        // Server socket will accept connections
        SocketBase server = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        Assertions.assertNotNull(server);
        SocketBase client = ZMQ.socket(ctx, ZMQ.ZMQ_DEALER);
        Assertions.assertNotNull(client);

        ZMQ.setSocketOption(server, ZMQ.ZMQ_ZAP_DOMAIN, "DOMAIN");
        ZMQ.setSocketOption(server, ZMQ.ZMQ_SELFADDR_PROPERTY_NAME, "X-Local-Address");
        rc = ZMQ.bind(server, host);
        Assertions.assertTrue(rc);
        rc = ZMQ.connect(client, host);
        Assertions.assertTrue(rc);

        int ret = ZMQ.send(client, "This is a message", 0);
        Assertions.assertEquals(17, ret);

        Msg msg = ZMQ.recv(server, 0);
        Assertions.assertNotNull(msg);

        String prop = ZMQ.getMessageMetadata(msg, "Socket-Type");
        Assertions.assertEquals("DEALER", prop);

        prop = ZMQ.getMessageMetadata(msg, "User-Id");
        Assertions.assertEquals("anonymous", prop);

        prop = ZMQ.getMessageMetadata(msg, "Peer-Address");
        Assertions.assertTrue(prop.startsWith("127.0.0.1:"));

        prop = ZMQ.getMessageMetadata(msg, "no such");
        Assertions.assertNull(prop);

        prop = ZMQ.getMessageMetadata(msg, "Hello");
        Assertions.assertEquals("World", prop);

        prop = ZMQ.getMessageMetadata(msg, "X-Local-Address");
        Assertions.assertEquals("127.0.0.1:" + port, prop);

        ZMQ.closeZeroLinger(server);
        ZMQ.closeZeroLinger(client);

        // Shutdown
        ZMQ.term(ctx);

        // Wait until ZAP handler terminates
        thread.join();
    }

    @Test
    void testWriteRead() throws IOException
    {
        Map<String, String> srcMap = new HashMap<>();
        srcMap.put("keyEmpty", "");
        srcMap.put("keyNull", null);
        Metadata fromMap = new Metadata(srcMap);

        Assertions.assertEquals("", fromMap.get("keyEmpty"));
        Assertions.assertEquals("", fromMap.get("keyNull"));

        Metadata src = new Metadata();
        src.put("key", "value");
        src.put("keyEmpty", "");
        src.put("keyNull", null);
        src.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        src.write(stream);
        byte[] array = stream.toByteArray();

        Metadata dst = new Metadata();
        dst.read(ByteBuffer.wrap(array), 0, null);

        Assertions.assertEquals(src, dst);
        Assertions.assertEquals("", dst.get("keyEmpty"));
        Assertions.assertEquals("", dst.get("keyNull"));
    }
}
