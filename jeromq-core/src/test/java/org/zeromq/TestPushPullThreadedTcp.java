package org.zeromq;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.ZMQ.Socket;

/**
 * Tests a PUSH-PULL dialog with several methods, each component being on a
 * separate thread.
 */
class TestPushPullThreadedTcp
{
    private static Logger logger = LogManager.getLogger(TestPushPullThreadedTcp.class);

    private static class Worker implements Runnable {
        private final int count;
        private final AtomicBoolean finished = new AtomicBoolean();
        private int idx;
        private final Socket receiver;

        public Worker(Socket receiver, int count)
        {
            this.receiver = receiver;
            this.count = count;
        }

        @Override
        public void run()
        {
            idx = 0;
            while (idx < count) {
                if (idx % 5000 == 10) {
                    zmq.ZMQ.msleep(100);
                }
                ZMsg msg = ZMsg.recvMsg(receiver);
                msg.destroy();
                idx++;
            }
            finished.set(true);
        }
    }

    private static class Client implements Runnable
    {
        private final Socket sender;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final int count;

        public Client(Socket sender, int count)
        {
            this.sender = sender;
            this.count = count;
        }

        @Override
        public void run()
        {
            int idx = 0;
            while (idx++ < count) {
                ZMsg msg = new ZMsg();
                msg.add("DATA");
                boolean sent = msg.send(sender);
                Assertions.assertTrue(sent);
            }
            finished.set(true);
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testPushPull1() throws Exception {
        test(1);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testPushPull500() throws Exception {
        test(500);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testPushPullWithWatermark() throws Exception {
        logger.info("Sending 20000 messages to trigger watermark limit");
        test(20000);
        logger.info("testPushPullWithWatermark completed successfully");
    }

    private void test(int count) throws InterruptedException {
        logger.debug("Test started with {} messages", count);

        ExecutorService threadPool = Executors.newFixedThreadPool(2);

        ZContext ctx = new ZContext();

        ZMQ.Socket receiver = ctx.createSocket(SocketType.PULL);
        Assertions.assertNotNull(receiver);
        receiver.setImmediate(false);
        int port = receiver.bindToRandomPort("tcp://localhost");

        ZMQ.Socket sender = ctx.createSocket(SocketType.PUSH);
        Assertions.assertNotNull(sender);
        boolean rc = sender.connect("tcp://localhost:" + port);
        Assertions.assertTrue(rc);

        Worker worker = new Worker(receiver, count);
        Client client = new Client(sender, count);

        threadPool.submit(worker);
        threadPool.submit(client);

        threadPool.shutdown();
        threadPool.awaitTermination(10, TimeUnit.SECONDS);

        logger.info("Worker received {} messages", worker.idx);
        Assertions.assertTrue(client.finished.get(), "Unable to send messages");
        Assertions.assertTrue(worker.finished.get(), "Unable to receive messages");

        receiver.close();
        sender.close();
        ctx.close();
    }
}
