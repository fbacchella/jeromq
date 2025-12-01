package org.zeromq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

class ParanoidPiratServerWithLazyPiratClientTest
{
    private static final Logger logger = LogManager.getLogger(ParanoidPiratServerWithLazyPiratClientTest.class);

    private static final int HEARTBEAT_LIVENESS = 3;    //  3-5 is reasonable
    private static final int HEARTBEAT_INTERVAL = 1000; //  msecs

    //  Paranoid Pirate Protocol constants
    private static final String PPP_READY     = "\001"; //  Signals worker is ready
    private static final String PPP_HEARTBEAT = "\002"; //  Signals worker heartbeat

    private static void failTest(String desc, ZMsg msg)
    {
        StringBuilder builder = new StringBuilder(desc);
        msg.dump(builder);
        logger.error("Test failure: {}", builder);
        Assertions.fail(builder.toString());
    }

    private static final class Queue implements Runnable
    {
        private final int portQueue;
        private final int portWorkers;

        private final AtomicBoolean active = new AtomicBoolean(true);

        //  Here we define the worker class; a structure and a set of functions that
        //  as constructor, destructor, and methods on worker objects:
        private static class Worker
        {
            final ZFrame address;  //  Address of worker
            final String identity; //  Printable identity
            final long   expiry;   //  Expires at this time

            protected Worker(ZFrame address)
            {
                this.address = address;
                identity = new String(address.getData(), ZMQ.CHARSET);
                expiry = System.currentTimeMillis() + HEARTBEAT_INTERVAL * HEARTBEAT_LIVENESS;
            }

            //  The ready method puts a worker to the end of the ready list:
            protected void ready(final List<Worker> workers)
            {
                final Iterator<Worker> it = workers.iterator();
                while (it.hasNext()) {
                    final Worker worker = it.next();
                    if (identity.equals(worker.identity)) {
                        it.remove();
                        break;
                    }
                }
                workers.add(this);
            }

            //  The next method returns the next available worker address:
            protected static ZFrame next(final List<Worker> workers)
            {
                final Worker worker = workers.remove(0);
                Assertions.assertNotNull(worker);
                return worker.address;
            }

            //  The purge method looks for and kills expired workers. We hold workers
            //  from oldest to most recent, so we stop at the first alive worker:
            protected static void purge(List<Worker> workers)
            {
                Iterator<Worker> it = workers.iterator();
                while (it.hasNext()) {
                    Worker worker = it.next();
                    if (System.currentTimeMillis() < worker.expiry) {
                        break;
                    }
                    it.remove();
                }
            }
        }

        public Queue(int portQueue, int portWorkers)
        {
            this.portQueue = portQueue;
            this.portWorkers = portWorkers;
        }

        //  The main task is an LRU queue with heartbeating on workers so we can
        //  detect crashed or blocked worker tasks:
        @Override
        public void run()
        {
            Thread.currentThread().setName("Queue");
            ZContext ctx = new ZContext();
            Socket frontend = ctx.createSocket(SocketType.ROUTER);
            Socket backend = ctx.createSocket(SocketType.ROUTER);
            frontend.bind("tcp://*:" + portQueue); //  For clients
            backend.bind("tcp://*:" + portWorkers); //  For workers

            //  List of available workers
            List<Worker> workers = new ArrayList<>();

            //  Send out heartbeats at regular intervals
            long heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;

            Poller poller = ctx.createPoller(2);
            poller.register(backend, Poller.POLLIN);
            poller.register(frontend, Poller.POLLIN);

            while (active.get()) {
                boolean workersAvailable = !workers.isEmpty();
                int rc = poller.poll(HEARTBEAT_INTERVAL);
                if (rc == -1) {
                    break; //  Interrupted
                }

                //  Handle worker activity on backend
                if (poller.pollin(0)) {
                    //  Use worker address for LRU routing
                    ZMsg msg = ZMsg.recvMsg(backend);
                    if (msg == null) {
                        break; //  Interrupted
                    }

                    //  Any sign of life from worker means it's ready
                    ZFrame address = msg.unwrap();
                    Worker worker = new Worker(address);
                    worker.ready(workers);

                    //  Validate a control message or return a reply to a client
                    if (msg.size() == 1) {
                        ZFrame frame = msg.getFirst();
                        String data = new String(frame.getData(), ZMQ.CHARSET);
                        if (!data.equals(PPP_READY) && !data.equals(PPP_HEARTBEAT)) {
                            failTest("Queue ---- invalid message from worker", msg);
                        }
                        msg.destroy();
                    }
                    else {
                        msg.send(frontend);
                    }
                }
                if (workersAvailable && poller.pollin(1)) {
                    //  Now get next client request, route to next worker
                    ZMsg msg = ZMsg.recvMsg(frontend);
                    if (msg == null) {
                        break; //  Interrupted
                    }
                    msg.push(Worker.next(workers));
                    msg.send(backend);
                }

                //  We handle heartbeating after any socket activity. First we send
                //  heartbeats to any idle workers if it's time. Then we purge any
                //  dead workers:

                if (System.currentTimeMillis() >= heartbeatAt) {
                    for (Worker worker : workers) {
                        worker.address.send(backend, ZFrame.REUSE + ZFrame.MORE);
                        ZFrame frame = new ZFrame(PPP_HEARTBEAT);
                        frame.send(backend, 0);
                    }
                    heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;
                }
                Worker.purge(workers);
            }

            //  When we're done, clean up properly
            workers.clear();
            ctx.close();
        }

    }

    private static final class Worker implements Runnable
    {
        private final int portWorkers;

        private static final int INTERVAL_INIT = 1000;  //  Initial reconnect
        private static final int INTERVAL_MAX  = 32000; //  After exponential backoff

        //  Helper function that returns a new configured socket
        //  connected to the Paranoid Pirate queue

        private Worker(int portWorkers)
        {
            this.portWorkers = portWorkers;
        }

        private Socket workerSocket(ZContext ctx)
        {
            Socket worker = ctx.createSocket(SocketType.DEALER);
            worker.connect("tcp://localhost:" + portWorkers);

            //  Tell queue we're ready for work
            logger.info("ready");
            ZFrame frame = new ZFrame(PPP_READY);
            frame.send(worker, 0);

            return worker;
        }

        //  We have a single task, which implements the worker side of the
        //  Paranoid Pirate Protocol (PPP). The interesting parts here are
        //  the heartbeating, which lets the worker detect if the queue has
        //  died, and vice-versa:

        @Override
        public void run()
        {
            Thread.currentThread().setName("Worker");
            ZContext ctx = new ZContext();
            Socket worker = workerSocket(ctx);

            Poller poller = ctx.createPoller(1);
            poller.register(worker, Poller.POLLIN);

            //  If liveness hits zero, queue is considered disconnected
            int liveness = HEARTBEAT_LIVENESS;
            int interval = INTERVAL_INIT;

            //  Send out heartbeats at regular intervals
            long heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;

            int cycles = 0;
            while (true) {
                int rc = poller.poll(HEARTBEAT_INTERVAL);
                if (rc == -1) {
                    break; //  Interrupted
                }

                if (poller.pollin(0)) {
                    //  Get message
                    //  - 3-part envelope + content -> request
                    //  - 1-part HEARTBEAT -> heartbeat
                    ZMsg msg = ZMsg.recvMsg(worker);
                    if (msg == null) {
                        break; //  Interrupted
                    }

                    //  To test the robustness of the queue implementation we
                    //  simulate various typical problems, such as the worker
                    //  crashing, or running very slowly. We do this after a few
                    //  cycles so that the architecture can get up and running
                    //  first:
                    if (msg.size() == 3) {
                        cycles++;
                        if (cycles % 10 == 0) {
                            logger.info("Simulating a crash");
                            msg.destroy();
                            break;
                        }
                        else if (cycles % 5 == 0) {
                            logger.info("Simulating CPU overload");
                            try {
                                Thread.sleep(3000);
                            }
                            catch (InterruptedException e) {
                                break;
                            }
                        }
                        logger.info("Normal reply");
                        msg.send(worker);
                        liveness = HEARTBEAT_LIVENESS;
                        try {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            break;
                        } //  Do some heavy work
                    }
                    else
                        //  When we get a heartbeat message from the queue, it means the
                        //  queue was (recently) alive, so reset our liveness indicator:
                        if (msg.size() == 1) {
                            final ZFrame frame = msg.getFirst();
                            if (PPP_HEARTBEAT.equals(new String(frame.getData(), ZMQ.CHARSET))) {
                                liveness = HEARTBEAT_LIVENESS;
                            }
                            else {
                                failTest("Invalid message", msg);
                            }
                            msg.destroy();
                        }
                        else {
                            failTest("Invalid message", msg);
                        }
                    interval = INTERVAL_INIT;
                }
                else
                    //  If the queue hasn't sent us heartbeats in a while, destroy the
                    //  socket and reconnect. This is the simplest most brutal way of
                    //  discarding any messages we might have sent in the meantime://
                    if (--liveness == 0) {
                        logger.warn("Heartbeat failure, can't reach queue");
                        logger.warn("Reconnecting in {} msec", interval);
                        try {
                            Thread.sleep(interval);
                        }
                        catch (InterruptedException e) {
                            logger.error("Worker interrupted during reconnection sleep", e);
                        }

                        if (interval < INTERVAL_MAX) {
                            interval *= 2;
                        }
                        worker.close();
                        worker = workerSocket(ctx);
                        liveness = HEARTBEAT_LIVENESS;
                    }

                //  Send heartbeat to queue if it's time
                if (System.currentTimeMillis() > heartbeatAt) {
                    heartbeatAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL;
                    logger.info("Heartbeat");
                    ZFrame frame = new ZFrame(PPP_HEARTBEAT);
                    frame.send(worker, 0);
                }
            }
            ctx.close();
        }
    }

    private static class Client implements Runnable
    {
        private final int portQueue;

        private static final int REQUEST_TIMEOUT = 2500; //  msecs, (> 1000!)
        private static final int REQUEST_RETRIES = 3;    //  Before we abandon

        public Client(int portQueue)
        {
            this.portQueue = portQueue;
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("Client");
            ZContext ctx = new ZContext();
            logger.info("Connecting to server");
            Socket client = ctx.createSocket(SocketType.REQ);
            Assertions.assertNotNull(client);
            client.connect("tcp://localhost:" + portQueue);

            Poller poller = ctx.createPoller(1);
            poller.register(client, Poller.POLLIN);

            int sequence = 0;
            int retriesLeft = REQUEST_RETRIES;
            while (retriesLeft > 0 && !Thread.currentThread().isInterrupted()) {
                //  We send a request, then we work to get a reply
                String request = String.format("%d", ++sequence);
                client.send(request);

                int expectReply = 1;
                while (expectReply > 0) {
                    //  Poll socket for a reply, with timeout
                    int rc = poller.poll(REQUEST_TIMEOUT);
                    if (rc == -1) {
                        break; //  Interrupted
                    }

                    //  Here we process a server reply and exit our loop if the
                    //  reply is valid. If we don't get a reply, we close the client
                    //  socket and resend the request. We try a number of times
                    //  before finally abandoning:

                    if (poller.pollin(0)) {
                        //  We got a reply from the server, must match getSequence
                        String reply = client.recvStr();
                        if (reply == null) {
                            break; //  Interrupted
                        }
                        if (Integer.parseInt(reply) == sequence) {
                            logger.info("Server replied OK ({})", reply);
                            retriesLeft = REQUEST_RETRIES;
                            expectReply = 0;
                        }
                        else {
                            logger.error("Malformed reply from server: {}", reply);
                        }

                    }
                    else if (--retriesLeft == 0) {
                        logger.error("Server seems to be offline, abandoning");
                        break;
                    }
                    else {
                        logger.warn("No response from server, retrying");
                        // The old socket is confused; close it and open a new one
                        poller.unregister(client);
                        client.close();
                        logger.info("Reconnecting to server");
                        client = ctx.createSocket(SocketType.REQ);
                        client.connect("tcp://localhost:" + portQueue);
                        poller.register(client, Poller.POLLIN);
                        //  Send request again, on a new socket
                        client.send(request);
                    }
                }
            }
            ctx.close();
        }
    }

    @Test
    void testIssue408() throws IOException, InterruptedException, ExecutionException
    {
        int portQueue = Utils.findOpenPort();
        int portWorkers = Utils.findOpenPort();

        ExecutorService service = Executors.newFixedThreadPool(4);
        Queue queue = new Queue(portQueue, portWorkers);
        service.submit(queue);
        Future<?> worker = service.submit(new Worker(portWorkers));
        service.submit(() -> {
            Thread.currentThread().setName("Rebooter");
            try {
                worker.get();
                logger.info("Restarting new worker after crash");
                service.submit(new Worker(portWorkers));
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error in worker rebooter", e);
            }
        });
        Future<?> client = service.submit(new Client(portQueue));

        client.get();
        // client is terminated, time to stop the queue to complete the test.
        queue.active.set(false);

        service.shutdown();
        Assertions.assertTrue(service.awaitTermination(20, TimeUnit.SECONDS));
    }
}
