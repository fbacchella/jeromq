package org.zeromq;

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
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.zeromq.ZMQ.Socket;

import zmq.Msg;
import zmq.ZError;
import zmq.io.Metadata;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class TlsTest {

    private static SSLContext ssl;

    @BeforeAll
    static void initCa() throws GeneralSecurityException, OperatorCreationException, IOException
    {
        KeyStore ks = AutoCA.getKeyStore("cn=localhost", InetAddress.getLoopbackAddress());
        ssl = AutoCA.createSSLContext(ks);
    }

    @Test
    void testSimple() {
        assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
            try (ZContext ctx = new ZContext(1);
                    Socket pull = ctx.createSocket(SocketType.PULL);
                    Socket push = ctx.createSocket(SocketType.PUSH)
            ) {
                SSLParameters params = ssl.getDefaultSSLParameters();
                params.setWantClientAuth(true);
                AtomicReference<Principal> principalReference = new AtomicReference<>();
                Function<SSLSession, Optional<String>> principalConverter = s -> {
                    try {
                        principalReference.set(s.getPeerPrincipal());
                        return Optional.ofNullable(s.getPeerPrincipal().getName());
                    } catch (SSLPeerUnverifiedException e) {
                        return Optional.empty();
                    }
                };
                TlsChannelFactory clientWrapper = TlsChannelFactory.newBuilder().setCtx(ssl).build();
                TlsChannelFactory serverWrapper = TlsChannelFactory.newBuilder().setCtx(ssl).setParameters(params).setPrincipalConverter(principalConverter).build();
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
                System.err.println(ZError.toString(push.base().errno.get()));
                System.err.println(ZError.toString(pull.base().errno.get()));
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
        assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
            CompletableFuture<Throwable> future = new CompletableFuture<>();
            try (ZContext ctx = getNewContext(future);
                    Socket pull = ctx.createSocket(SocketType.PULL);
                    Socket push = ctx.createSocket(SocketType.PUSH)
            ) {
                SSLContext defaultCtx = SSLContext.getDefault();
                TlsChannelFactory clientWrapper = TlsChannelFactory.newBuilder().setCtx(defaultCtx).build();
                TlsChannelFactory serverWrapper = TlsChannelFactory.newBuilder().setCtx(ssl).build();
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
        assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
            CompletableFuture<Throwable> future = new CompletableFuture<>();
            try (ZContext ctx = getNewContext(future);
                    Socket pull = ctx.createSocket(SocketType.PULL);
                    Socket push = ctx.createSocket(SocketType.PUSH)
            ) {
                SSLParameters sslv12 = ssl.getDefaultSSLParameters();
                SSLParameters sslv13 = ssl.getDefaultSSLParameters();
                sslv12.setProtocols(new String[]{"TLSv1.2"});
                sslv13.setProtocols(new String[]{"TLSv1.3"});
                TlsChannelFactory clientWrapper = TlsChannelFactory.newBuilder().setCtx(ssl).setParameters(sslv12).build();
                TlsChannelFactory serverWrapper = TlsChannelFactory.newBuilder().setCtx(ssl).setParameters(sslv13).build();
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
