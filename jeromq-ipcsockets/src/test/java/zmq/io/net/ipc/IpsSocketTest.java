package zmq.io.net.ipc;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import zmq.Options;
import zmq.io.net.SocketFactory;
import zmq.io.net.ServerSocketWrapper;
import zmq.io.net.SocketWrapper;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledForJreRange(min = JRE.JAVA_16)
class IpsSocketTest {

    @TempDir
    Path tempDir;

    @Test
    void rawTest() throws IOException, InterruptedException, ExecutionException, TimeoutException
    {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(tempDir.resolve("zmq-test.sock"));
        SocketFactory<UnixDomainSocketAddress> factory = new IpcChannelFactory();
        CountDownLatch latch1 = new CountDownLatch(1);
        CompletableFuture<Exception> futureException = new CompletableFuture<>();
        CompletableFuture<String> futureMessage = new CompletableFuture<>();
        Runnable r = () -> {
                try (ServerSocketWrapper<UnixDomainSocketAddress> serverSocket = factory.makeServerSocket(new Options())) {
                    serverSocket.bind(address);
                    latch1.countDown();
                    try (SocketWrapper<UnixDomainSocketAddress> wrapped = serverSocket.accept(null)) {
                        ByteBuffer res = ByteBuffer.allocate(256);
                        while (wrapped.read(res) != -1) {
                            res.flip();
                            futureMessage.complete(StandardCharsets.UTF_8.decode(res).toString());
                            res.compact();
                        }
                    }
               } catch (IOException | RuntimeException ex) {
                    futureException.complete(ex);
                }
        };
        Thread t = new Thread(r);
        t.setName("Server");
        t.start();
        Assertions.assertTrue(latch1.await(2, TimeUnit.SECONDS));
        String expected = "Hi !";
        // connect raw socket channel normally
        try (SocketWrapper<UnixDomainSocketAddress> wrapped = factory.makeSocket(new Options())){
            wrapped.connect(address);
            wrapped.write(ByteBuffer.wrap(expected.getBytes(StandardCharsets.UTF_8)));
        }
        t.interrupt();
        Assertions.assertEquals(ClosedByInterruptException.class, futureException.get(2, TimeUnit.SECONDS).getClass());
        Assertions.assertEquals(expected, futureMessage.get(2, TimeUnit.SECONDS));
    }

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
