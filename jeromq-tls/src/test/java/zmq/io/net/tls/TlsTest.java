package zmq.io.net.tls;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.zeromq.Events;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZEvent;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import zmq.Msg;
import zmq.io.Metadata;

class TlsTest {

    // To be executed before LogManager.getLogger() to ensure that log4j2 will use the basic context selector
    // Not the smart one for web app.
    static {
        //System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector");
        //System.setProperty("log4j.shutdownHookEnabled", "false");
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        System.setProperty("log4j2.julLoggerAdapter", "org.apache.logging.log4j.jul.CoreLoggerAdapter");
        //Configurator.setRootLevel(Level.TRACE);
        //Configurator.setLevel("tlschannel", Level.ALL);
        java.util.logging.Logger.getLogger("tlschannel").severe("severe");
        java.util.logging.Logger.getLogger("tlschannel").warning("warning");
        java.util.logging.Logger.getLogger("tlschannel").fine("fine");
        java.util.logging.Logger.getLogger("tlschannel").finest("finest");
    }

    private static SSLContext ssl;

    @BeforeAll
    static void initCa() throws GeneralSecurityException, OperatorCreationException, IOException
    {
        KeyStore ks = AutoCA.getKeyStore("cn=localhost", InetAddress.getLoopbackAddress());
        ssl = AutoCA.createSSLContext(ks);
    }

    @Test
    void tesExplicitFactory() {
        Assertions.assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
            try (ZContext ctx = new ZContext(1);
                    Socket pull = ctx.createSocket(SocketType.PULL);
                    Socket push = ctx.createSocket(SocketType.PUSH)
            ) {
                SSLParameters params = ssl.getDefaultSSLParameters();
                params.setWantClientAuth(true);
                AtomicReference<Principal> principalReference = new AtomicReference<>();
                PrincipalConverter principalConverter = s -> {
                    try {
                        principalReference.set(s.getPeerPrincipal());
                        return Optional.ofNullable(s.getPeerPrincipal().getName());
                    } catch (SSLPeerUnverifiedException e) {
                        return Optional.empty();
                    }
                };
                TlsSocketFactory clientWrapper = TlsSocketFactory.newBuilder().setCtx(ssl).build();
                TlsSocketFactory serverWrapper = TlsSocketFactory.newBuilder().setCtx(ssl).setParameters(params).setPrincipalConverter(principalConverter).build();
                pull.setChannelWrapper(serverWrapper);
                push.setChannelWrapper(clientWrapper);
                Assertions.assertEquals(serverWrapper, pull.getChannelWrapper());
                Assertions.assertEquals(clientWrapper, push.getChannelWrapper());

                CompletableFuture<Throwable> fPush = new CompletableFuture<>();
                CompletableFuture<Throwable> fPull = new CompletableFuture<>();
                AtomicInteger disconnect = new AtomicInteger();
                push.setEventHook(e -> eventConsumer(e, fPush, disconnect), ZMQ.EVENT_ALL);
                pull.setEventHook(e -> eventConsumer(e, fPull, disconnect), ZMQ.EVENT_ALL);

                int port = pull.bindToRandomPort("tcp://*");
                push.connect("tcp://127.0.0.1:" + port);

                String expected = "Hello";
                push.send(expected);
                Msg msg = pull.recvMsg(0);
                Assertions.assertEquals(expected, new String(msg.data(), StandardCharsets.UTF_8));
                Assertions.assertEquals("CN=localhost", msg.getMetadata().get(Metadata.USER_ID));
                Assertions.assertEquals(X500Principal.class, principalReference.get().getClass());
            }
        });
    }

    @Test
    void testByAddr() {
        Assertions.assertTimeoutPreemptively(Duration.ofDays(1000), () -> {
            try (ZContext ctx = new ZContext(1);
                    Socket pull = ctx.createSocket(SocketType.PULL);
                    Socket push = ctx.createSocket(SocketType.PUSH)
            ) {
                SSLParameters params = ssl.getDefaultSSLParameters();
                params.setWantClientAuth(true);
                AtomicReference<Principal> principalReference = new AtomicReference<>();
                PrincipalConverter principalConverter = s -> {
                    try {
                        principalReference.set(s.getPeerPrincipal());
                        return Optional.ofNullable(s.getPeerPrincipal().getName());
                    } catch (SSLPeerUnverifiedException e) {
                        return Optional.empty();
                    }
                };
                push.setSslContext(ssl);
                pull.setSslContext(ssl);
                pull.setSslParameters(params);
                pull.setPrincipalConvert(principalConverter);
                CompletableFuture<Throwable> fPush = new CompletableFuture<>();
                CompletableFuture<Throwable> fPull = new CompletableFuture<>();
                AtomicInteger disconnect = new AtomicInteger();
                push.setEventHook(e -> eventConsumer(e, fPush, disconnect), ZMQ.EVENT_ALL);
                pull.setEventHook(e -> eventConsumer(e, fPull, disconnect), ZMQ.EVENT_ALL);

                //int port = pull.bindToRandomPort("tls://*");
                int port = 34228;
                pull.bind("tls://*:" + port);
                push.connect("tls://127.0.0.1:" + port);

                String expected = "Hello";
                push.send(expected);
                Msg msg = pull.recvMsg(0);
                Assertions.assertEquals(expected, new String(msg.data(), StandardCharsets.UTF_8));
                Assertions.assertEquals("CN=localhost", msg.getMetadata().get(Metadata.USER_ID));
                Assertions.assertEquals(X500Principal.class, principalReference.get().getClass());
            }
        });
    }

    private ZContext getNewContext(CompletableFuture<Throwable> future)
    {
        ZContext ctx = new ZContext(1);
        ctx.setNotificationExceptionHandler((t, e) -> future.complete(e));
        return ctx;
    }

    @Test
    public void testFailed() {
        Assertions.assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
            CompletableFuture<Throwable> future = new CompletableFuture<>();
            try (ZContext ctx = getNewContext(future);
                    Socket pull = ctx.createSocket(SocketType.PULL);
                    Socket push = ctx.createSocket(SocketType.PUSH)
            ) {
                SSLContext defaultCtx = SSLContext.getDefault();
                TlsSocketFactory clientWrapper = TlsSocketFactory.newBuilder().setCtx(defaultCtx).build();
                TlsSocketFactory serverWrapper = TlsSocketFactory.newBuilder().setCtx(ssl).build();
                pull.setChannelWrapper(serverWrapper);
                push.setChannelWrapper(clientWrapper);
                Assertions.assertEquals(serverWrapper, pull.getChannelWrapper());
                Assertions.assertEquals(clientWrapper, push.getChannelWrapper());

                CompletableFuture<Throwable> fPush = new CompletableFuture<>();
                CompletableFuture<Throwable> fPull = new CompletableFuture<>();
                AtomicInteger disconnect = new AtomicInteger();
                push.setEventHook(e -> eventConsumer(e, fPush, disconnect), ZMQ.EVENT_ALL);
                pull.setEventHook(e -> eventConsumer(e, fPull, disconnect), ZMQ.EVENT_ALL);

                int port = pull.bindToRandomPort("tcp://*");
                push.connect("tcp://127.0.0.1:" + port);

                String expected = "Hello";
                Assertions.assertTrue(push.send(expected));
                Assertions.assertEquals(SSLHandshakeException.class, fPush.get().getClass());
                fPull.get();
                Assertions.assertEquals(2, disconnect.get());
                Assertions.assertTrue(push.send(expected));
            }
        });
    }

    @Test
    void testFailedSSL() {
        Assertions.assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
            CompletableFuture<Throwable> future = new CompletableFuture<>();
            try (ZContext ctx = getNewContext(future);
                    Socket pull = ctx.createSocket(SocketType.PULL);
                    Socket push = ctx.createSocket(SocketType.PUSH)
            ) {
                SSLParameters sslv12 = ssl.getDefaultSSLParameters();
                SSLParameters sslv13 = ssl.getDefaultSSLParameters();
                sslv12.setProtocols(new String[]{"TLSv1.2"});
                sslv13.setProtocols(new String[]{"TLSv1.3"});
                TlsSocketFactory clientWrapper = TlsSocketFactory.newBuilder().setCtx(ssl).setParameters(sslv12).build();
                TlsSocketFactory serverWrapper = TlsSocketFactory.newBuilder().setCtx(ssl).setParameters(sslv13).build();
                pull.setChannelWrapper(serverWrapper);
                push.setChannelWrapper(clientWrapper);
                Assertions.assertEquals(serverWrapper, pull.getChannelWrapper());
                Assertions.assertEquals(clientWrapper, push.getChannelWrapper());

                CompletableFuture<Throwable> fPush = new CompletableFuture<>();
                CompletableFuture<Throwable> fPull = new CompletableFuture<>();
                AtomicInteger disconnect = new AtomicInteger();
                push.setEventHook(e -> eventConsumer(e, fPush, disconnect), ZMQ.EVENT_ALL);
                pull.setEventHook(e -> eventConsumer(e, fPull, disconnect), ZMQ.EVENT_ALL);

                int port = pull.bindToRandomPort("tcp://*");
                push.connect("tcp://127.0.0.1:" + port);

                String expected = "Hello";
                Assertions.assertTrue(push.send(expected));
                Assertions.assertTrue(push.send(expected));
                Assertions.assertEquals(SSLHandshakeException.class, fPull.get().getClass());
                Assertions.assertEquals(ClosedChannelException.class, fPush.get().getClass());
            }
        });
    }

    private void eventConsumer(ZEvent e, CompletableFuture<Throwable> future, AtomicInteger disconnect)
    {
        if (e.getEvent() == Events.EXCEPTION) {
            Throwable ex = e.getValue();
            future.complete(ex);
        } else if (e.getEvent() == Events.DISCONNECTED){
            disconnect.incrementAndGet();
        } else {
            System.err.format("%s %s%n", e.getAddress(), e);
        }
    }
}
