package zmq.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import zmq.Ctx;
import zmq.Msg;
import zmq.SocketBase;
import zmq.ZError;
import zmq.ZMQ;
import zmq.ZMQ.Event;
import zmq.util.TestUtils;

public abstract class AbstractProtocolVersion
{
    protected static final int REPETITIONS = 1000;
    private static final AtomicReference<Throwable> monitorFailure = new AtomicReference<>();
    private static final Logger logger = LogManager.getLogger(AbstractProtocolVersion.class);

    static class SocketMonitor extends Thread
    {
        private final Ctx         ctx;
        private final String      monitorAddr;
        private final ZMQ.Event[] events = new ZMQ.Event[1];

        public SocketMonitor(Ctx ctx, String monitorAddr)
        {
            this.ctx = ctx;
            this.monitorAddr = monitorAddr;
            monitorFailure.set(null);
            this.setUncaughtExceptionHandler((t, ex) -> {
                logger.error("Uncaught exception in SocketMonitor", ex);
                monitorFailure.set(ex);
            });
        }

        @Override
        public void run()
        {
            SocketBase s = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
            boolean rc = s.connect(monitorAddr);
            Assertions.assertTrue(rc);
            // Only some of the exceptional events could fire

            ZMQ.Event event = ZMQ.Event.read(s);
            if (event == null && s.errno() == ZError.ETERM) {
                s.close();
                return;
            }
            Assertions.assertNotNull(event);

            events[0] = event;
            s.close();
        }
    }

    protected byte[] assertProtocolVersion(int version, List<ByteBuffer> raws, String payload)
            throws IOException, InterruptedException
    {
        String host = "tcp://localhost:*";

        Ctx ctx = ZMQ.init(1);
        Assertions.assertNotNull(ctx);

        SocketBase receiver = ZMQ.socket(ctx, ZMQ.ZMQ_PULL);
        Assertions.assertNotNull(receiver);

        boolean rc = ZMQ.setSocketOption(receiver, ZMQ.ZMQ_LINGER, 0);
        Assertions.assertTrue(rc);

        rc = ZMQ.monitorSocket(receiver, "inproc://monitor", ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL);
        Assertions.assertTrue(rc);

        SocketMonitor monitor = new SocketMonitor(ctx, "inproc://monitor");
        monitor.start();

        rc = ZMQ.bind(receiver, host);
        Assertions.assertTrue(rc);

        String ep = (String) ZMQ.getSocketOptionExt(receiver, ZMQ.ZMQ_LAST_ENDPOINT);
        int port = TestUtils.port(ep);
        Socket sender = new Socket("127.0.0.1", port);
        OutputStream out = sender.getOutputStream();
        for (ByteBuffer raw : raws) {
            out.write(raw.array());
        }
        Assertions.assertNull(monitorFailure.get());

        Msg msg = ZMQ.recv(receiver, 0);
        Assertions.assertNotNull(msg);
        Assertions.assertEquals(payload, new String(msg.data(), ZMQ.CHARSET));

        monitor.join();
        Event event = monitor.events[0];
        Assertions.assertNotNull(event);
        Assertions.assertEquals(ZMQ.ZMQ_EVENT_HANDSHAKE_PROTOCOL, event.event);
        Assertions.assertEquals(version, (Integer) event.arg);

        InputStream in = sender.getInputStream();
        byte[] data = new byte[255];
        int read = in.read(data);

        sender.close();

        ZMQ.close(receiver);
        ZMQ.term(ctx);

        return Arrays.copyOf(data, read);
    }

    protected List<ByteBuffer> raws(int revision)
    {
        List<ByteBuffer> raws = new ArrayList<>();
        ByteBuffer raw = ByteBuffer.allocate(12);
        // send V1 header
        raw.put((byte) 0xff).put(new byte[8]).put((byte) 0x1);
        // protocol revision
        raw.put((byte) revision);
        // socket type
        raw.put((byte) ZMQ.ZMQ_PUSH);

        raws.add(raw);
        return raws;
    }

    protected ByteBuffer identity()
    {
        return ByteBuffer.allocate(2)
                // size
                .put((byte) 1)
                // flags
                .put((byte) 0);
    }
}
