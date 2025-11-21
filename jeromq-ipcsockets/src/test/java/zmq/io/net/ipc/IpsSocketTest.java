package zmq.io.net.ipc;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import zmq.Helper;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledForJreRange(min = JRE.JAVA_16)
class IpsSocketTest {

    @TempDir
    Path tempDir;

    @Test
    void testPairIpc() {
        String addr = "ipc://" + tempDir.resolve("zmq-test.sock");

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            try (ZContext ctx = new ZContext(1);
                    Socket pairConnect = ctx.createSocket(SocketType.PAIR);
                    Socket pairBind = ctx.createSocket(SocketType.PAIR)) {
                assertTrue(pairBind.bind(addr));
                assertTrue(pairConnect.connect(addr));
                AtomicReference<Throwable> ex = new AtomicReference<>();
                pairConnect.setEventHook(e -> ex.set(e.getValue()), ZMQ.ZMQ_EVENT_EXCEPTION);
                pairBind.setEventHook(e -> ex.set(e.getValue()), ZMQ.ZMQ_EVENT_EXCEPTION);
                Helper.bounce(pairBind.base(), pairConnect.base());
                Assertions.assertNull(ex.get());
            }
        });
    }

}
