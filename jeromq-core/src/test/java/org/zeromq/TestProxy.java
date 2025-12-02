package org.zeromq;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.util.ZData;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class TestProxy {
    private static final Logger logger = LogManager.getLogger(TestProxy.class);

    static class Client implements Runnable
    {
        private final String frontend;
        private final String name;
        private final AtomicBoolean result = new AtomicBoolean();

        Client(String name, String frontend)
        {
            this.name = name;
            this.frontend = frontend;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            Assertions.assertNotNull(ctx);

            Socket socket = ctx.socket(SocketType.REQ);
            boolean rc;
            rc = socket.setIdentity(id(name));
            Assertions.assertTrue(rc);

            logger.info("Start {}", name);
            Thread.currentThread().setName(name);

            rc = socket.connect(frontend);
            Assertions.assertTrue(rc);

            result.set(process(socket));
            socket.close();
            ctx.close();
            logger.info("Stop {}", name);
        }

        private boolean process(Socket socket)
        {
            boolean rc = socket.send("hello");
            if (!rc) {
                logger.error("{} unable to send first message", name);
                return false;
            }
            logger.debug("{} sent 1st message", name);
            String msg = socket.recvStr(0);
            logger.debug("{} received {}", name, msg);
            if (msg == null || !msg.startsWith("OK hello")) {
                return false;
            }
            rc = socket.send("world");
            if (!rc) {
                logger.error("{} unable to send second message", name);
                return false;
            }
            msg = socket.recvStr(0);
            logger.debug("{} received {}", name, msg);
            return msg != null && msg.startsWith("OK world");
        }
    }

    static class Dealer implements Runnable
    {
        private final String backend;
        private final String name;
        private final AtomicBoolean result = new AtomicBoolean();

        public Dealer(String name, String backend)
        {
            this.name = name;
            this.backend = backend;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            Assertions.assertNotNull(ctx);

            Thread.currentThread().setName(name);
            logger.info("Start {}", name);

            Socket socket = ctx.socket(SocketType.DEALER);
            boolean rc;
            rc = socket.setIdentity(id(name));
            Assertions.assertTrue(rc);
            rc = socket.connect(backend);
            Assertions.assertTrue(rc);

            result.set(process(socket));
            socket.close();
            ctx.close();
            logger.info("Stop {}", name);
        }

        private boolean process(Socket socket)
        {
            int count = 0;
            while (count < 2) {
                byte[] msg = socket.recv(0);
                String msgAsString = new String(msg, ZMQ.CHARSET);
                if (!msgAsString.startsWith("Client-")) {
                    logger.error("{} Wrong identity {}", name, msgAsString);
                    return false;
                }
                byte[] identity = msg;
                logger.debug("{} received client identity {}", name, ZData.strhex(identity));

                msg = socket.recv(0);
                msgAsString = new String(msg, ZMQ.CHARSET);
                if (msg.length != 0) {
                    logger.error("Not bottom {}", Arrays.toString(msg));
                    return false;
                }
                logger.debug("{} received bottom {}", name, msgAsString);

                msg = socket.recv(0);
                if (msg == null) {
                    logger.error("{} Not data {}", name, msg);
                    return false;
                }
                msgAsString = new String(msg, ZMQ.CHARSET);
                logger.debug("{} received data {}", name, msgAsString);

                socket.send(identity, ZMQ.SNDMORE);
                socket.send((byte[]) null, ZMQ.SNDMORE);

                String response = "OK " + msgAsString + " " + name;

                socket.send(response, 0);
                count++;
            }
            return true;
        }
    }

    static class Proxy extends Thread
    {
        private final String frontend;
        private final String backend;
        private final String control;
        private final AtomicBoolean result = new AtomicBoolean();

        Proxy(String frontend, String backend, String control)
        {
            this.frontend = frontend;
            this.backend = backend;
            this.control = control;
        }

        @Override
        public void run()
        {
            Context ctx = ZMQ.context(1);
            Assertions.assertNotNull(ctx);

            setName("Proxy");
            Socket frontend = ctx.socket(SocketType.ROUTER);

            Assertions.assertNotNull(frontend);
            frontend.bind(this.frontend);

            Socket backend = ctx.socket(SocketType.DEALER);
            Assertions.assertNotNull(backend);
            backend.bind(this.backend);

            Socket control = ctx.socket(SocketType.PAIR);
            Assertions.assertNotNull(control);
            control.bind(this.control);

            ZMQ.proxy(frontend, backend, null, control);

            frontend.close();
            backend.close();
            control.close();
            ctx.close();
            result.set(true);
        }
    }

    private static byte[] id(String name)
    {
        Random random = new Random();
        byte[] id = new byte[10 + random.nextInt(245)];
        random.nextBytes(id);
        System.arraycopy(name.getBytes(ZMQ.CHARSET), 0, id, 0, name.length());
        return id;
    }

    @Test
    @Timeout(value = 50, unit = TimeUnit.SECONDS)
    void testProxy() throws IOException, InterruptedException
    {
        logger.info("Starting testProxy");
        String frontend = "tcp://localhost:" + Utils.findOpenPort();
        String backend = "tcp://localhost:" + Utils.findOpenPort();
        String controlEndpoint = "tcp://localhost:" + Utils.findOpenPort();

        Proxy proxy = new Proxy(frontend, backend, controlEndpoint);
        proxy.start();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        Dealer d1 = new Dealer("Dealer-A", backend);
        Dealer d2 = new Dealer("Dealer-B", backend);
        executor.submit(d1);
        executor.submit(d2);

        Thread.sleep(1000);
        Client c1 = new Client("Client-X", frontend);
        Client c2 = new Client("Client-Y", frontend);
        executor.submit(c1);
        executor.submit(c2);

        executor.shutdown();
        executor.awaitTermination(40, TimeUnit.SECONDS);

        Context ctx = ZMQ.context(1);
        Socket control = ctx.socket(SocketType.PAIR);
        control.connect(controlEndpoint);
        control.send(ZMQ.PROXY_TERMINATE);
        proxy.join();
        control.close();
        ctx.close();

        Assertions.assertTrue(c1.result.get());
        Assertions.assertTrue(c2.result.get());
        Assertions.assertTrue(d1.result.get());
        Assertions.assertTrue(d2.result.get());
        Assertions.assertTrue(proxy.result.get());
        logger.info("testProxy completed successfully");
    }
}
