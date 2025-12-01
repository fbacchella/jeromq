package org.zeromq;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import zmq.util.AndroidProblematic;

public class HighWatermarkTest
{
    private static final Logger logger = LogManager.getLogger(HighWatermarkTest.class);

    public static final int N_MESSAGES   = 30000;
    public static final int MESSAGE_SIZE = 50;

    public static final int FILL_WATERMARK = 3000;
    public static final int TRACE          = 7000;

    public static class Dispatcher implements Runnable
    {
        private final String control;
        private final String dispatch;
        private final String msg;

        Dispatcher(String msg, String dispatch, String control)
        {
            this.msg = msg;
            this.dispatch = dispatch;
            this.control = control;
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Dispatcher");

            ZContext context = new ZContext(1);

            //  Socket to send messages on
            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.setImmediate(false);
            sender.bind(dispatch);

            ZMQ.Socket controller = context.createSocket(SocketType.SUB);
            controller.subscribe(ZMQ.SUBSCRIPTION_ALL);
            controller.connect(control);

            try {
                logger.info("Sending {} tasks ({}b) to workers", N_MESSAGES, MESSAGE_SIZE);

                //  The first message is "0" and signals start of batch
                sender.send("0", 0);

                logger.info("Started dispatcher on {}", dispatch);

                //  Send N_MESSAGES tasks
                for (int taskNbr = 0; taskNbr < N_MESSAGES; taskNbr++) {
                    sender.send(taskNbr + " - " + msg, 0);
                    logger.debug("{} - Dispatcher sent msg", taskNbr);
                }

                logger.info("Dispatcher finished, awaiting for collector finish");
                controller.recvStr();
                // We can finish NOW!
            }
            finally {
                logger.debug("Dispatcher closing.");
                context.close();
                logger.info("Dispatcher done.");
            }
        }
    }

    static class Worker implements Runnable
    {
        private final String control;
        private final String dispatch;
        private final String collect;
        private final int index;

        Worker(String dispatch, String collect, String control, int index)
        {
            this.dispatch = dispatch;
            this.collect = collect;
            this.control = control;
            this.index = index;
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Worker #" + index);

            ZContext context = new ZContext(1);

            //  Socket to receive messages on
            ZMQ.Socket receiver = context.createSocket(SocketType.PULL);
            receiver.setImmediate(false);
            receiver.connect(dispatch);

            //  Socket to send messages to
            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.setImmediate(false);
            sender.connect(collect);

            ZMQ.Socket controller = context.createSocket(SocketType.SUB);
            controller.subscribe("FINISH");
            controller.connect(control);

            ZMQ.Poller poller = context.createPoller(3);
            poller.register(receiver, ZMQ.Poller.POLLIN);
            poller.register(sender, ZMQ.Poller.POLLOUT);
            poller.register(controller, ZMQ.Poller.POLLIN);

            int idx = 0;

            try {
                logger.info("Started worker process #{}", index);

                //  Process tasks forever
                while (!Thread.currentThread().isInterrupted()) {
                    poller.poll(1000);
                    boolean in = poller.pollin(0);
                    boolean out = poller.pollout(1);
                    boolean ctrl = poller.pollin(2);
                    if (in && out) {
                        String msg = new String(receiver.recv(0), ZMQ.CHARSET).trim();
                        //  Simple progress indicator for the viewer
                        logger.debug("#{} recv {}", index, msg);

                        if (idx % TRACE == 0) {
                            logger.info("#{} recv {} messages", index, idx);
                        }

                        ++idx;
                        // the pipes reach the watermark once in a while
                        if (idx % FILL_WATERMARK == 10) {
                            LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS));
                        }

                        //  Send results to sink
                        sender.send("#" + index + " - " + msg, 0);
                    }
                    if (ctrl) {
                        break;
                    }
                }
            }
            finally {
                logger.debug("#{} closing.", index);
                poller.close();
                context.close();
                logger.debug("#{} done.", index);
            }
        }
    }

    static class Collector implements Runnable
    {
        private final String control;
        private final String collect;
        private final String msg;
        private final int workers;
        private final AtomicBoolean success = new AtomicBoolean();

        public Collector(String msg, String collect, String control, int workers)
        {
            this.msg = msg;
            this.collect = collect;
            this.control = control;
            this.workers = workers;
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Collector");
            logger.debug("Started collector on {}", collect);

            //  Prepare our context and socket
            ZContext context = new ZContext(1);

            ZMQ.Socket receiver = context.createSocket(SocketType.PULL);
            receiver.setImmediate(false);
            receiver.bind(collect);

            ZMQ.Socket controller = context.createSocket(SocketType.PUB);
            controller.bind(control);

            try {
                //  Wait for start of batch
                String msg;

                logger.debug("Started");

                for (int taskNbr = 0; taskNbr < N_MESSAGES; taskNbr++) {
                    if (taskNbr % FILL_WATERMARK == 10) {
                        LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS));
                    }
                    msg = new String(receiver.recv(0), ZMQ.CHARSET).trim();
                    logger.debug("recv : {} -> {}", taskNbr, msg);

                    if (taskNbr % TRACE == 0 || taskNbr == 100) {
                        logger.info("recv : {} messages", taskNbr);
                    }

                    // Test received messages
                    if (workers == 1) {
                        if (msg.indexOf(" - " + taskNbr + " - ") != 2) {
                            logger.error("{} - Message was not correct ! {}", taskNbr, msg);
                            break;
                        }
                    }
                    if (!msg.endsWith(this.msg) && !msg.endsWith(" - 0")) {
                        logger.error("{} - Message was not correct ! {}", taskNbr, msg);
                        break;
                    }
                }

                controller.send("FINISH"); // Signal dispatcher to finish
            }
            finally {
                context.close();
                logger.info("Done.");
            }

            success.set(true);
        }
    }

    @Test
    void testReliabilityOnWatermark() throws IOException, InterruptedException
    {
        testWatermark(1);
    }

    @Test
    @AndroidProblematic
    void testReliabilityOnWatermark2() throws IOException, InterruptedException
    {
        testWatermark(2);
    }

    private void testWatermark(int workers) throws IOException, InterruptedException
    {
        ExecutorService threadPool = Executors.newFixedThreadPool(workers + 2);

        String control = "tcp://localhost:" + Utils.findOpenPort();
        String collect = "tcp://localhost:" + Utils.findOpenPort();
        String dispatch = "tcp://localhost:" + Utils.findOpenPort();

        String msg = randomString(MESSAGE_SIZE);

        Dispatcher dispatcher = new Dispatcher(msg, dispatch, control);
        Collector collector = new Collector(msg, collect, control, workers);
        threadPool.submit(dispatcher);
        threadPool.submit(collector);
        for (int idx = 0; idx < workers; ++idx) {
            threadPool.submit(new Worker(dispatch, collect, control, idx + 1));
        }

        threadPool.shutdown();
        threadPool.awaitTermination(120, TimeUnit.SECONDS);

        Assertions.assertTrue(collector.success.get());
    }

    /*--------------------------------------------------------------*/

    private static final String ABC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom rnd = new SecureRandom();

    // http://stackoverflow.com/a/157202
    private static String randomString(int len)
    {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ABC.charAt(rnd.nextInt(ABC.length())));
        }
        return sb.toString();
    }
}
