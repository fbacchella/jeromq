package zmq;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import zmq.util.Utils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

class ImmediateTest
{
    private static Logger logger = LogManager.getLogger(ImmediateTest.class);

    @Test
    void testImmediateTrue() throws Exception
    {
        logger.info("Immediate = true");
        // TEST 1.
        // First we're going to attempt to send messages to two
        // pipes, one connected, the other not. We should see
        // the PUSH load balancing to both pipes, and hence half
        // of the messages getting queued, as connect() creates a
        // pipe immediately.

        int pushPort1 = Utils.findOpenPort();
        int pushPort2 = Utils.findOpenPort();

        Ctx context = ZMQ.createContext();
        Assertions.assertNotNull(context);

        SocketBase to = ZMQ.socket(context, ZMQ.ZMQ_PULL);
        Assertions.assertNotNull(to);

        int val = 0;
        boolean rc = ZMQ.setSocketOption(to, ZMQ.ZMQ_LINGER, val);
        Assertions.assertTrue(rc);
        rc = ZMQ.bind(to, "tcp://*:" + pushPort1);
        Assertions.assertTrue(rc);

        // Create a socket pushing to two endpoints - only 1 message should arrive.
        SocketBase from = ZMQ.socket(context, ZMQ.ZMQ_PUSH);
        Assertions.assertNotNull(from);

        rc = ZMQ.setSocketOption(from, ZMQ.ZMQ_IMMEDIATE, true);
        Assertions.assertTrue(rc);

        val = 0;
        rc = ZMQ.setSocketOption(from, ZMQ.ZMQ_LINGER, val);
        Assertions.assertTrue(rc);
        // This pipe will not connect
        rc = ZMQ.connect(from, "tcp://localhost:" + pushPort2);
        Assertions.assertTrue(rc);
        // This pipe will
        rc = ZMQ.connect(from, "tcp://localhost:" + pushPort1);
        Assertions.assertTrue(rc);

        // We send 10 messages, 5 should just get stuck in the queue
        // for the not-yet-connected pipe
        for (int i = 0; i < 10; ++i) {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.send(from, message, 0);
            Assertions.assertTrue(sent >= 0);
        }

        ZMQ.sleep(1);
        // We now consume from the connected pipe
        // - we should see just 5
        int timeout = 250;
        ZMQ.setSocketOption(to, ZMQ.ZMQ_RCVTIMEO, timeout);

        int seen = 0;
        for (int i = 0; i < 10; ++i) {
            Msg msg = ZMQ.recv(to, 0);
            if (msg == null) {
                break; //  Break when we didn't get a message
            }
            seen++;
        }
        Assertions.assertEquals(5, seen);

        ZMQ.close(from);
        ZMQ.close(to);
        ZMQ.term(context);
    }

    @Test
    void testImmediateFalse() throws IOException {
        logger.info("Immediate = false");
        // TEST 2
        // This time we will do the same thing, connect two pipes,
        // one of which will succeed in connecting to a bound
        // receiver, the other of which will fail. However, we will
        // also set the delay attach on connect flag, which should
        // cause the pipe attachment to be delayed until the connection
        // succeeds.
        int validPort = Utils.findOpenPort();
        int invalidPort = Utils.findOpenPort();
        Ctx context = ZMQ.createContext();

        SocketBase to = ZMQ.socket(context, ZMQ.ZMQ_PULL);
        Assertions.assertNotNull(to);
        boolean rc = ZMQ.bind(to, "tcp://*:" + validPort);
        Assertions.assertTrue(rc);

        int val = 0;
        rc = ZMQ.setSocketOption(to, ZMQ.ZMQ_LINGER, val);
        Assertions.assertTrue(rc);

        // Create a socket pushing to two endpoints - all messages should arrive.
        SocketBase from = ZMQ.socket(context, ZMQ.ZMQ_PUSH);
        Assertions.assertNotNull(from);

        val = 0;
        rc = ZMQ.setSocketOption(from, ZMQ.ZMQ_LINGER, val);
        Assertions.assertTrue(rc);

        // Set the key flag
        rc = ZMQ.setSocketOption(from, ZMQ.ZMQ_IMMEDIATE, false);
        Assertions.assertTrue(rc);

        // Connect to the invalid socket
        rc = ZMQ.connect(from, "tcp://localhost:" + invalidPort);
        Assertions.assertTrue(rc);
        // Connect to the valid socket
        rc = ZMQ.connect(from, "tcp://localhost:" + validPort);
        Assertions.assertTrue(rc);

        for (int i = 0; i < 10; ++i) {
            String message = "message ";
            message += ('0' + i);
            int sent = ZMQ.send(from, message, 0);
            Assertions.assertEquals(message.length(), sent);
        }

        int timeout = 250;
        ZMQ.setSocketOption(to, ZMQ.ZMQ_RCVTIMEO, timeout);

        int seen = 0;
        for (int i = 0; i < 10; ++i) {
            Msg msg = ZMQ.recv(to, 0);
            if (msg == null) {
                break;
            }
            seen++;
        }
        Assertions.assertEquals(10, seen);

        ZMQ.close(from);
        ZMQ.close(to);
        ZMQ.term(context);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testImmediateFalseWithBrokenConnection() throws IOException {
        logger.info("Immediate = false with broken connection");
        // TEST 3
        // This time we want to validate that the same blocking behaviour
        // occurs with an existing connection that is broken. We will send
        // messages to a connected pipe, disconnect and verify the messages
        // block. Then we reconnect and verify messages flow again.
        int port = Utils.findOpenPort();
        Ctx context = ZMQ.createContext();

        SocketBase backend = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        Assertions.assertNotNull(backend);

        SocketBase frontend = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        Assertions.assertNotNull(frontend);

        int linger = 0;
        ZMQ.setSocketOption(backend, ZMQ.ZMQ_LINGER, linger);
        ZMQ.setSocketOption(frontend, ZMQ.ZMQ_LINGER, linger);

        //  Frontend connects to backend using IMMEDIATE
        ZMQ.setSocketOption(frontend, ZMQ.ZMQ_IMMEDIATE, false);

        boolean rc = ZMQ.bind(backend, "tcp://*:" + port);
        Assertions.assertTrue(rc);

        rc = ZMQ.connect(frontend, "tcp://localhost:" + port);
        Assertions.assertTrue(rc);

        logger.debug("Connection established");
        //  Ping backend to frontend so we know when the connection is up
        int sent = ZMQ.send(backend, "Hello", 0);
        Assertions.assertEquals(5, sent);
        logger.debug("Ping sent");
        Msg msg = ZMQ.recv(frontend, 0);
        logger.debug("Ping received");
        Assertions.assertEquals(5, msg.size());

        // Send message from frontend to backend
        sent = ZMQ.send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        Assertions.assertEquals(5, sent);

        logger.debug("Message sent");
        ZMQ.close(backend);
        logger.debug("Backend closed");

        logger.debug("Testing message send failure");
        //  Send a message, should fail
        //  There's no way to do this except with a sleep and a loop
        while (ZMQ.send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT) != -1) {
            ZMQ.sleep(2);
        }

        //  Recreate backend socket
        backend = ZMQ.socket(context, ZMQ.ZMQ_DEALER);
        ZMQ.setSocketOption(backend, ZMQ.ZMQ_LINGER, linger);
        rc = ZMQ.bind(backend, "tcp://*:" + port);
        Assertions.assertTrue(rc);

        logger.debug("Backend recreated");
        //  Ping backend to frontend so we know when the connection is up
        sent = ZMQ.send(backend, "Hello", 0);
        Assertions.assertEquals(5, sent);
        logger.debug("Ping sent after reconnect");
        msg = ZMQ.recv(frontend, 0);
        logger.debug("Ping received after reconnect");
        Assertions.assertEquals(5, msg.size());

        logger.debug("Testing message send after reconnect");
        // After the reconnect, should succeed
        sent = ZMQ.send(frontend, "Hello", ZMQ.ZMQ_DONTWAIT);
        Assertions.assertEquals(5, sent);

        ZMQ.close(backend);
        ZMQ.close(frontend);
        ZMQ.term(context);
    }
}
