package zmq.socket.pair;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

import zmq.Ctx;
import zmq.Helper;
import zmq.SocketBase;
import zmq.ZMQ;

import static org.junit.jupiter.api.Assertions.*;

class TestPairUnixDomainSocketIpc {

    @TempDir
    Path tempDir;

    @Test
    void testPairIpc() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Ctx ctx = ZMQ.init(1);
            assertNotNull(ctx);

            SocketBase pairBind = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
            assertNotNull(pairBind);

            Path temp = tempDir.resolve("zmq-test.sock");
            assertTrue(ZMQ.bind(pairBind, "ipc://" + temp));

            SocketBase pairConnect = ZMQ.socket(ctx, ZMQ.ZMQ_PAIR);
            assertNotNull(pairConnect);

            boolean brc = ZMQ.connect(pairConnect, "ipc://" + temp);
            assertTrue(brc);

            Helper.bounce(pairBind, pairConnect);

            ZMQ.close(pairBind);
            ZMQ.close(pairConnect);
            ZMQ.term(ctx);
        });
    }

 }
