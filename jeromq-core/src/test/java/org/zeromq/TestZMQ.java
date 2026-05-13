package org.zeromq;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import zmq.SocketBase;
import zmq.ZError;
import zmq.io.mechanism.MechanismSettings;
import zmq.io.mechanism.Mechanisms;
import zmq.io.mechanism.NullMechanism;
import zmq.io.mechanism.curve.CurveMechanismSettings;
import zmq.io.mechanism.plain.PlainMechanismSettings;
import zmq.io.net.tls.PrincipalConverter;
import zmq.msg.MsgAllocator;
import zmq.msg.MsgAllocatorDirect;
import zmq.msg.MsgAllocatorHeap;
import zmq.socket.pubsub.Sub;
import zmq.socket.pubsub.XPub;
import zmq.socket.reqrep.Req;
import zmq.socket.reqrep.Router;
import zmq.util.Errno;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class TestZMQ
{
    @Rule
    public TestRule timeout = Timeout.seconds(10);

    private Context ctx;

    @Before
    public void setUp()
    {
        ctx = ZMQ.context(1);
        assertThat(ctx, notNullValue());
        new Errno().set(0);
    }

    @After
    public void tearDown()
    {
        ctx.close();
    }

    @Test
    public void testErrno()
    {
        try (Socket socket = ctx.socket(SocketType.DEALER)) {
            assertThat(socket.errno(), is(0));
        }
    }

    @Test(expected = ZMQException.class)
    public void testBindSameAddress() throws IOException
    {
        int port = Utils.findOpenPort();
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket socket1 = context.socket(SocketType.REQ);
        socket1.bind("tcp://*:" + port);
        try (socket1; Socket socket2 = context.socket(SocketType.REQ)) {
            socket2.bind("tcp://*:" + port);
            fail("Exception not thrown");
        }
        catch (ZMQException e) {
            assertEquals(e.getErrorCode(), Errors.EADDRINUSE.getCode());
            throw e;
        }
        finally {
            context.term();
        }
    }

    @Test(expected = ZMQException.class)
    public void testBindInprocSameAddress()
    {
        ZMQ.Context context = ZMQ.context(1);

        ZMQ.Socket socket1 = context.socket(SocketType.REQ);
        ZMQ.Socket socket2 = context.socket(SocketType.REQ);
        socket1.bind("inproc://address.already.in.use");

        socket2.bind("inproc://address.already.in.use");
        assertThat(socket2.errno(), is(ZError.EADDRINUSE));

        socket1.close();
        socket2.close();

        context.term();
    }

    @Test
    public void testSocketUnbind()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        System.out.println("Connecting socket to unbind on port " + port);
        byte[] data = "ABC".getBytes();

        rc = push.send(data);
        assertThat(rc, is(true));
        assertArrayEquals(data, pull.recv());

        rc = pull.unbind("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        rc = push.send(data);
        assertThat(rc, is(true));
        assertNull(pull.recv());

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testSocketSendRecvArray()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        byte[] data = "ABC".getBytes(ZMQ.CHARSET);

        rc = push.sendMore("DEF");
        assertThat(rc, is(true));
        rc = push.send(data, 0, data.length - 1, 0);
        assertThat(rc, is(true));

        byte[] recvd = pull.recv();
        assertThat(recvd, is("DEF".getBytes(ZMQ.CHARSET)));

        byte[] datb = new byte[2];
        int received = pull.recv(datb, 0, datb.length, 0);
        assertThat(received, is(2));
        assertThat(datb, is("AB".getBytes(ZMQ.CHARSET)));

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testContextBlocky()
    {
        Socket router = ctx.socket(SocketType.ROUTER);
        long rc = router.getLinger();
        assertThat(rc, is(-1L));

        router.close();
        ctx.setBlocky(false);

        router = ctx.socket(SocketType.ROUTER);

        rc = router.getLinger();
        assertThat(rc, is(0L));

        router.close();
    }

    @Test(timeout = 1000)
    public void testSocketDoubleClose()
    {
        try (Socket socket = ctx.socket(SocketType.PUSH)) {
            socket.close();
        }
    }

    @Test
    public void testSubscribe()
    {
        try (ZMQ.Socket socket = ctx.socket(SocketType.SUB)) {
            boolean rc = socket.subscribe("abc");
            assertThat(rc, is(true));

            rc = socket.unsubscribe("abc");
            assertThat(rc, is(true));

            rc = socket.unsubscribe("abc".getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));
        }
    }

    @Test
    public void testSocketAffinity()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());
            socket.setAffinity(42);
            long rc = socket.getAffinity();

            assertThat(rc, is(42L));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketBacklog()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setBacklog(42L);
            assertThat(set, is(true));
            int rc = socket.getBacklog();
            assertThat(rc, is(42));
        }
    }

    @Test
    public void testSocketConflate()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setConflate(true);
            assertThat(set, is(true));
            boolean rc = socket.getConflate();
            assertThat(rc, is(true));

            set = socket.setConflate(false);
            assertThat(set, is(true));
            rc = socket.isConflate();
            assertThat(rc, is(false));
        }
    }

    @Test
    public void testSocketConnectRid()
    {
        try (final Socket socket = ctx.socket(SocketType.ROUTER)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setConnectRid("rid");
            assertThat(set, is(true));

            set = socket.setConnectRid("rid".getBytes(ZMQ.CHARSET));
            assertThat(set, is(true));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketCurveAsServer()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());
            boolean rc = socket.setMechanism(CurveMechanismSettings.getBuilder()
                                                                   .generateKey()
                                                                   .build());
            assertThat(rc, is(true));

            boolean server = socket.getCurveServer();
            assertThat(server, is(true));

            server = socket.getAsServerCurve();
            assertThat(server, is(true));

            server = socket.isAsServerCurve();
            assertThat(server, is(true));

            CurveMechanismSettings mechanism = socket.getMechanism();
            assertThat(mechanism.getMechanism(), is(Mechanisms.CURVE));
            assertThat(mechanism.isServer(), is(true));
        }
    }

    @Test
    public void testSocketCurveSecret()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            byte[] publicKey = new byte[32];
            Arrays.fill(publicKey, (byte) 0x1);
            byte[] secretKey = new byte[32];
            Arrays.fill(secretKey, (byte) 0x2);

            boolean rc = socket.setMechanism(CurveMechanismSettings.getBuilder()
                                                                   .setPublicKey(publicKey)
                                                                   .setSecretKey(secretKey)
                                                                   .build());
            assertThat(rc, is(true));

            byte[] curve = socket.getCurveSecretKey();
            assertThat(curve, is(secretKey));

            boolean server = socket.getCurveServer();
            assertThat(server, is(true));

            CurveMechanismSettings mechanism = socket.getMechanism();
            assertThat(mechanism.getMechanism(), is(Mechanisms.CURVE));
            assertThat(mechanism.secretKey(), is(secretKey));
        }
    }

    @Test
    public void testSocketCurvePublic()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            MechanismSettings mechanism = socket.getMechanism();
            assertThat(mechanism.getMechanism(), is(Mechanisms.NULL));

            byte[] publicKey = new byte[32];
            Arrays.fill(publicKey, (byte) 0x1);
            byte[] secretKey = new byte[32];
            Arrays.fill(secretKey, (byte) 0x2);

            boolean rc = socket.setMechanism(CurveMechanismSettings.getBuilder()
                                                                   .setPublicKey(publicKey)
                                                                   .setSecretKey(secretKey)
                                                                   .build());
            assertThat(rc, is(true));

            byte[] curve = socket.getCurvePublicKey();
            assertThat(curve, is(publicKey));

            boolean server = socket.getCurveServer();
            assertThat(server, is(true));

            CurveMechanismSettings mech = socket.getMechanism();
            assertThat(mech.getMechanism(), is(Mechanisms.CURVE));
            assertThat(mech.publicKey(), is(publicKey));
        }
    }

    @Test
    public void testSocketCurveServer()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            byte[] publicKey = new byte[32];
            Arrays.fill(publicKey, (byte) 0x1);
            byte[] secretKey = new byte[32];
            Arrays.fill(secretKey, (byte) 0x2);
            byte[] serverKey = new byte[32];
            Arrays.fill(serverKey, (byte) 0x3);

            boolean rc = socket.setMechanism(CurveMechanismSettings.getBuilder()
                                                                   .setPublicKey(publicKey)
                                                                   .setSecretKey(secretKey)
                                                                   .setServerKey(serverKey)
                                                                   .build());
            assertThat(rc, is(true));

            byte[] curve = socket.getCurveServerKey();
            assertThat(curve, is(serverKey));

            boolean server = socket.getCurveServer();
            assertThat(server, is(false));

            CurveMechanismSettings mechanism = socket.getMechanism();
            assertThat(mechanism.getMechanism(), is(Mechanisms.CURVE));
            assertThat(mechanism.serverKey(), is(serverKey));
        }
    }

    @Test
    public void testSocketHandshake()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setHandshakeIvl(42);
            assertThat(set, is(true));
            int rc = socket.getHandshakeIvl();
            assertThat(rc, is(42));
        }
    }

    @Test
    public void testSocketHeartbeatIvl()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setHeartbeatIvl(42);
            assertThat(set, is(true));
            int rc = socket.getHeartbeatIvl();
            assertThat(rc, is(42));
        }
    }

    @Test
    public void testSocketHeartbeatTtl()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setHeartbeatTtl(420);
            assertThat(set, is(true));
            int rc = socket.getHeartbeatTtl();
            assertThat(rc, is(400));
        }
    }

    @Test
    public void testSocketHeartbeatTimeout()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setHeartbeatTimeout(42);
            assertThat(set, is(true));
            int rc = socket.getHeartbeatTimeout();
            assertThat(rc, is(42));
        }
    }

    @Test
    public void testSocketHeartbeatContext()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            byte[] context = new byte[3];
            context[0] = 4;
            context[1] = 2;
            context[2] = 1;
            boolean set = socket.setHeartbeatContext(context);
            assertThat(set, is(true));
            byte[] hctx = socket.getHeartbeatContext();
            assertThat(hctx, is(context));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketHWM()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setHWM(42);
            assertThat(set, is(true));
            int rc = socket.getRcvHWM();
            assertThat(rc, is(42));

            rc = socket.getSndHWM();
            assertThat(rc, is(42));

            set = socket.setHWM(43L);
            assertThat(set, is(true));
            rc = socket.getRcvHWM();
            assertThat(rc, is(43));

            rc = socket.getSndHWM();
            assertThat(rc, is(43));

            rc = socket.getHWM();
            assertThat(rc, is(-1));
        }
    }

    @Test
    public void testSocketIdentity()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            byte[] identity = new byte[42];
            Arrays.fill(identity, (byte) 0x42);
            boolean set = socket.setIdentity(identity);
            assertThat(set, is(true));
            byte[] rc = socket.getIdentity();
            assertThat(rc, is(identity));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketImmediate()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setImmediate(false);
            assertThat(set, is(true));
            boolean rc = socket.getImmediate();
            assertThat(rc, is(false));

            rc = socket.getDelayAttachOnConnect();
            assertThat(rc, is(true));

            set = socket.setImmediate(true);
            assertThat(set, is(true));
            rc = socket.getImmediate();
            assertThat(rc, is(true));

            rc = socket.getDelayAttachOnConnect();
            assertThat(rc, is(false));

            set = socket.setDelayAttachOnConnect(false);
            assertThat(set, is(true));
            rc = socket.getImmediate();
            assertThat(rc, is(true));

            rc = socket.getDelayAttachOnConnect();
            assertThat(rc, is(false));

            set = socket.setDelayAttachOnConnect(true);
            assertThat(set, is(true));
            rc = socket.getImmediate();
            assertThat(rc, is(false));

            rc = socket.getDelayAttachOnConnect();
            assertThat(rc, is(true));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketIPv6()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setIPv6(true);
            assertThat(set, is(true));
            boolean rc = socket.getIPv6();
            assertThat(rc, is(true));
            rc = socket.getIPv4Only();
            assertThat(rc, is(false));

            set = socket.setIPv6(false);
            assertThat(set, is(true));
            rc = socket.getIPv6();
            assertThat(rc, is(false));
            rc = socket.getIPv4Only();
            assertThat(rc, is(true));

            set = socket.setIPv4Only(false);
            assertThat(set, is(true));
            rc = socket.getIPv6();
            assertThat(rc, is(true));
            rc = socket.getIPv4Only();
            assertThat(rc, is(false));

            set = socket.setIPv4Only(true);
            assertThat(set, is(true));
            rc = socket.getIPv6();
            assertThat(rc, is(false));
            rc = socket.getIPv4Only();
            assertThat(rc, is(true));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketLinger()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setLinger(42);
            assertThat(set, is(true));
            int rc = socket.getLinger();
            assertThat(rc, is(42));

            set = socket.setLinger(42L);
            assertThat(set, is(true));
            rc = socket.getLinger();
            assertThat(rc, is(42));
        }
    }

    @Test
    public void testSocketMaxMsgSize()
    {
        try (final Socket socket = ctx.socket(SocketType.STREAM)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setMaxMsgSize(42);
            assertThat(set, is(true));
            long rc = socket.getMaxMsgSize();
            assertThat(rc, is(42L));
        }
    }

    @Test
    public void testSocketMsgAllocationThreshold()
    {
        try (final Socket socket = ctx.socket(SocketType.STREAM)) {
            assertThat(socket, notNullValue());

            boolean set = socket.setMsgAllocationHeapThreshold(42);
            assertThat(set, is(true));
            int rc = socket.getMsgAllocationHeapThreshold();
            assertThat(rc, is(42));
        }
    }

    @Test
    public void testSocketMsgAllocator()
    {
        try (final Socket socket = ctx.socket(SocketType.STREAM)) {
            assertThat(socket, notNullValue());

            MsgAllocator allocator = new MsgAllocatorDirect();
            boolean set = socket.setMsgAllocator(allocator);
            assertThat(set, is(true));

            // TODO
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSocketMulticastHops()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);

        try (socket) {
            assertThat(socket, notNullValue());
            socket.setMulticastHops(42);
        }
    }

    @Test
    public void testSocketGetMulticastHops()
    {
        try (final Socket socket = ctx.socket(SocketType.STREAM)) {
            assertThat(socket, notNullValue());

            long rc = socket.getMulticastHops();
            assertThat(rc, is(1L));
        }
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketMulticastLoop()
    {
        final Socket socket = ctx.socket(SocketType.STREAM);

        try (socket) {
            assertThat(socket, notNullValue());
            socket.setMulticastLoop(true);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketHasMulticastLoop()
    {
        try (final Socket socket = ctx.socket(SocketType.STREAM)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.hasMulticastLoop();
            assertThat(rc, is(false));
        }
    }

    @Test
    public void testSocketPlainPassword()
    {
        try (Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setMechanism(new PlainMechanismSettings(false, "username", "password"));
            assertThat(rc, is(true));

            String password = socket.getPlainPassword();
            assertThat(password, is("password"));

            boolean server = socket.getPlainServer();
            assertThat(server, is(false));

            PlainMechanismSettings mechanism = socket.getMechanism();
            assertThat(mechanism.getMechanism(), is(Mechanisms.PLAIN));
            assertThat(mechanism.password(), is("password"));
        }
    }

    @Test
    public void testSocketPlainUsername()
    {
        try (Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());
            boolean rc = socket.setMechanism(new PlainMechanismSettings(false, "username", "password"));
            assertThat(rc, is(true));

            String username = socket.getPlainUsername();
            assertThat(username, is("username"));

            boolean server = socket.getPlainServer();
            assertThat(server, is(false));

            PlainMechanismSettings mechanism = socket.getMechanism();
            assertThat(mechanism.getMechanism(), is(Mechanisms.PLAIN));
            assertThat(mechanism.username(), is("username"));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketPlainServer()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setMechanism(new PlainMechanismSettings(true, "username", "password"));
            assertThat(rc, is(true));

            boolean server = socket.getPlainServer();
            assertThat(server, is(true));

            server = socket.isAsServerPlain();
            assertThat(server, is(true));

            server = socket.getAsServerPlain();
            assertThat(server, is(true));

            PlainMechanismSettings mechanism = socket.getMechanism();
            assertThat(mechanism.getMechanism(), is(Mechanisms.PLAIN));
            assertThat(mechanism.isServer(), is(true));
        }
    }

    @Test
    public void testSocketProbeRouter()
    {
        try (final Socket socket = ctx.socket(SocketType.ROUTER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setProbeRouter(true);
            assertThat(rc, is(true));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSocketRate()
    {
        final Socket socket = ctx.socket(SocketType.ROUTER);

        try (socket) {
            assertThat(socket, notNullValue());
            socket.setRate(42);
        }
    }

    @Test
    public void testSocketGetRate()
    {
        try (final Socket socket = ctx.socket(SocketType.ROUTER)) {
            assertThat(socket, notNullValue());

            long rate = socket.getRate();
            assertThat(rate, is(100L));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketRcvHwm()
    {
        try (final Socket socket = ctx.socket(SocketType.DEALER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setRcvHWM(42L);
            assertThat(rc, is(true));

            int hwm = socket.getRcvHWM();
            assertThat(hwm, is(42));

            hwm = socket.getSndHWM();
            assertThat(hwm, is(not(42)));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketSndHwm()
    {
        try (final Socket socket = ctx.socket(SocketType.DEALER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setSndHWM(42L);
            assertThat(rc, is(true));

            int hwm = socket.getSndHWM();
            assertThat(hwm, is(42));

            hwm = socket.getRcvHWM();
            assertThat(hwm, is(not(42)));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketReceiveBufferSize()
    {
        try (final Socket socket = ctx.socket(SocketType.DEALER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setReceiveBufferSize(42L);
            assertThat(rc, is(true));

            int size = socket.getReceiveBufferSize();
            assertThat(size, is(42));

            size = socket.getSendBufferSize();
            assertThat(size, is(not(42)));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketSendBufferSize()
    {
        try (final Socket socket = ctx.socket(SocketType.DEALER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setSendBufferSize(42L);
            assertThat(rc, is(true));

            int size = socket.getSendBufferSize();
            assertThat(size, is(42));

            size = socket.getReceiveBufferSize();
            assertThat(size, is(not(42)));
        }
    }

    @Test
    public void testSocketReceiveTimeOut()
    {
        try (final Socket socket = ctx.socket(SocketType.PAIR)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setReceiveTimeOut(42);
            assertThat(rc, is(true));

            int size = socket.getReceiveTimeOut();
            assertThat(size, is(42));

            size = socket.getSendTimeOut();
            assertThat(size, is(not(42)));
        }
    }

    @Test
    public void testSocketSendTimeOut()
    {
        try (final Socket socket = ctx.socket(SocketType.PAIR)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setSendTimeOut(42);
            assertThat(rc, is(true));

            int size = socket.getSendTimeOut();
            assertThat(size, is(42));

            size = socket.getReceiveTimeOut();
            assertThat(size, is(not(42)));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketReconnectIVL()
    {
        try (final Socket socket = ctx.socket(SocketType.REP)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setReconnectIVL(42L);
            assertThat(rc, is(true));

            int reconnect = socket.getReconnectIVL();
            assertThat(reconnect, is(42));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketReconnectIVLMax()
    {
        try (final Socket socket = ctx.socket(SocketType.REP)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setReconnectIVLMax(42L);
            assertThat(rc, is(true));

            int reconnect = socket.getReconnectIVLMax();
            assertThat(reconnect, is(42));
        }
    }

    @Test
    public void testSocketRecoveryInterval()
    {
        final Socket socket = ctx.socket(SocketType.REP);

        try (socket) {
            assertThat(socket, notNullValue());
            socket.setRecoveryInterval(42L);
        }
    }

    @Test
    public void testSocketgetRecoveryInterval()
    {
        try (final Socket socket = ctx.socket(SocketType.REP)) {
            assertThat(socket, notNullValue());

            long reconnect = socket.getRecoveryInterval();
            assertThat(reconnect, is(10000L));
        }
    }

    @Test
    public void testSocketReqCorrelate()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setReqCorrelate(true);
            assertThat(rc, is(true));
            rc = socket.setReqCorrelate(false);
            assertThat(rc, is(true));
        }
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketGetReqCorrelate()
    {
        final Socket socket = ctx.socket(SocketType.REQ);

        try (socket) {
            assertThat(socket, notNullValue());
            socket.getReqCorrelate();
        }
    }

    @Test
    public void testSocketReqRelaxed()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setReqRelaxed(true);
            assertThat(rc, is(true));
            rc = socket.setReqRelaxed(false);
            assertThat(rc, is(true));
        }
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketGetReqRelaxed()
    {
        final Socket socket = ctx.socket(SocketType.REQ);

        try (socket) {
            assertThat(socket, notNullValue());
            socket.getReqRelaxed();
        }
    }

    @Test
    public void testSocketRouterHandover()
    {
        try (final Socket socket = ctx.socket(SocketType.ROUTER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setRouterHandover(true);
            assertThat(rc, is(true));
        }
    }

    @Test
    public void testSocketRouterMandatory()
    {
        try (final Socket socket = ctx.socket(SocketType.ROUTER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setRouterMandatory(true);
            assertThat(rc, is(true));
        }
    }

    @Test
    public void testSocketRouterRaw()
    {
        try (final Socket socket = ctx.socket(SocketType.ROUTER)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setRouterRaw(true);
            assertThat(rc, is(true));
        }
    }

    @Test
    public void testSocketSocksProxy()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setSocksProxy("abc");
            assertThat(rc, is(true));

            String proxy = socket.getSocksProxy();
            assertThat(proxy, is("abc"));

            rc = socket.setSocksProxy("def".getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));

            proxy = socket.getSocksProxy();
            assertThat(proxy, is("def"));
        }
    }

    @SuppressWarnings("deprecation")
    @Test(expected = UnsupportedOperationException.class)
    public void testSocketSwap()
    {
        final Socket socket = ctx.socket(SocketType.REQ);

        try (socket) {
            assertThat(socket, notNullValue());
            socket.setSwap(42L);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketGetSwap()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            long rc = socket.getSwap();
            assertThat(rc, is(-1L));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSocketTCPKeepAlive()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setTCPKeepAlive(1L);
            assertThat(rc, is(true));

            int tcp = socket.getTCPKeepAlive();
            assertThat(tcp, is(1));

            long tcpl = socket.getTCPKeepAliveSetting();
            assertThat(tcpl, is(1L));
        }
    }

    @Test
    public void testSocketTCPKeepAliveCount()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setTCPKeepAliveCount(42);
            assertThat(rc, is(true));

            long tcp = socket.getTCPKeepAliveCount();
            assertThat(tcp, is(42L));
        }
    }

    @Test
    public void testSocketTCPKeepAliveInterval()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setTCPKeepAliveInterval(42);
            assertThat(rc, is(true));

            long tcp = socket.getTCPKeepAliveInterval();
            assertThat(tcp, is(42L));
        }
    }

    @Test
    public void testSocketTCPKeepAliveIdle()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setTCPKeepAliveIdle(42);
            assertThat(rc, is(true));

            long tcp = socket.getTCPKeepAliveIdle();
            assertThat(tcp, is(42L));
        }
    }

    @Test
    public void testSocketTos()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setTos(42);
            assertThat(rc, is(true));

            int tos = socket.getTos();
            assertThat(tos, is(42));
        }
    }

    @Test
    public void testSocketType()
    {
        try (final Socket socket = ctx.socket(SocketType.REQ)) {
            assertThat(socket, notNullValue());

            SocketType rc = socket.getSocketType();
            assertThat(rc, is(SocketType.REQ));
        }
    }

    @Test
    public void testSocketXpubNoDrop()
    {
        try (final Socket socket = ctx.socket(SocketType.XPUB)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setXpubNoDrop(true);
            assertThat(rc, is(true));
        }
    }

    @Test
    public void testSocketXpubVerbose()
    {
        try (final Socket socket = ctx.socket(SocketType.XPUB)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setXpubVerbose(true);
            assertThat(rc, is(true));
        }
    }

    @Test
    public void testSocketZAPDomain()
    {
        try (final Socket socket = ctx.socket(SocketType.XPUB)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setZAPDomain("abc");
            assertThat(rc, is(true));

            String domain = socket.getZAPDomain();
            assertThat(domain, is("abc"));

            domain = socket.getZapDomain();
            assertThat(domain, is("abc"));

            rc = socket.setZapDomain("def");
            assertThat(rc, is(true));

            domain = socket.getZapDomain();
            assertThat(domain, is("def"));

            domain = socket.getZAPDomain();
            assertThat(domain, is("def"));

            rc = socket.setZapDomain("ghi".getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));

            domain = socket.getZapDomain();
            assertThat(domain, is("ghi"));

            domain = socket.getZAPDomain();
            assertThat(domain, is("ghi"));

            rc = socket.setZAPDomain("jkl".getBytes(ZMQ.CHARSET));
            assertThat(rc, is(true));

            domain = socket.getZapDomain();
            assertThat(domain, is("jkl"));

            domain = socket.getZAPDomain();
            assertThat(domain, is("jkl"));
        }
    }

    @Test
    public void testSocketLocalAddressPropertyName()
    {
        try (final Socket socket = ctx.socket(SocketType.XPUB)) {
            assertThat(socket, notNullValue());

            boolean rc = socket.setSelfAddressPropertyName("X-LocalAddress");
            assertThat(rc, is(true));

            String propertyName = socket.getSelfAddressPropertyName();
            assertThat(propertyName, is("X-LocalAddress"));
        }
    }

    private boolean filterdedConsumer(Class<? extends SocketBase> clazz, Socket socket, Supplier<Boolean> run)
    {
        if (clazz.isAssignableFrom(socket.base().getClass())) {
            return run.get();
        }
        else {
            return true;
        }
    }

    private boolean unsupportedConsumer(Runnable run)
    {
        Assert.assertThrows(UnsupportedOperationException.class, run::run);
        return true;
    }

    @Test
    public void testAllValues() throws NoSuchAlgorithmException
    {
        Function<Socket, Boolean> unhandled = s -> false;
        Function<Socket, Object> nothing = s -> false;

        SSLContext sslContext = SSLContext.getDefault();
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        PrincipalConverter principalConverter = ss -> Optional.empty();
        // Too many arguments to use Map.ofEntries()
        Map<Integer, Function<Socket, Boolean>> setters = new HashMap<>();
        setters.put(zmq.ZMQ.ZMQ_AFFINITY,            s -> s.setAffinity(1L));
        setters.put(zmq.ZMQ.ZMQ_IDENTITY,            s -> s.setIdentity(new byte[]{}));
        setters.put(zmq.ZMQ.ZMQ_SUBSCRIBE,           s -> filterdedConsumer(Sub.class, s, () -> s.subscribe(new byte[]{})));
        setters.put(zmq.ZMQ.ZMQ_UNSUBSCRIBE,         s -> filterdedConsumer(Sub.class, s, () -> s.unsubscribe(new byte[]{})));
        setters.put(zmq.ZMQ.ZMQ_RATE,                s -> unsupportedConsumer(() -> s.setRate(1L)));
        setters.put(zmq.ZMQ.ZMQ_RECOVERY_IVL,        s -> s.setRecoveryInterval(Duration.ofMillis(1)));
        setters.put(10,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_SNDBUF,              s -> s.setSendBufferSize(1));
        setters.put(zmq.ZMQ.ZMQ_RCVBUF,              s -> s.setReceiveBufferSize(1));
        setters.put(zmq.ZMQ.ZMQ_RCVMORE,             s -> unsupportedConsumer(() -> s.sendMore("")));
        setters.put(zmq.ZMQ.ZMQ_FD,                  unhandled);
        setters.put(zmq.ZMQ.ZMQ_EVENTS,              unhandled);
        setters.put(zmq.ZMQ.ZMQ_TYPE,                unhandled);
        setters.put(zmq.ZMQ.ZMQ_LINGER,              s -> s.setLinger(0));
        setters.put(zmq.ZMQ.ZMQ_RECONNECT_IVL,       s -> s.setReconnectIVL(1));
        setters.put(zmq.ZMQ.ZMQ_BACKLOG,             s -> s.setBacklog(1));
        setters.put(20,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_RECONNECT_IVL_MAX,   s -> s.setReconnectIVLMax(1));
        setters.put(zmq.ZMQ.ZMQ_MAXMSGSIZE,          s -> s.setMaxMsgSize(1L));
        setters.put(zmq.ZMQ.ZMQ_SNDHWM,              s -> s.setSndHWM(1));
        setters.put(zmq.ZMQ.ZMQ_RCVHWM,              s -> s.setRcvHWM(1));
        setters.put(zmq.ZMQ.ZMQ_MULTICAST_HOPS,      s -> unsupportedConsumer(() -> s.setMulticastHops(1L)));
        setters.put(26,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_RCVTIMEO,            s -> s.setReceiveTimeOut(50));
        setters.put(zmq.ZMQ.ZMQ_SNDTIMEO,            s -> s.setSendTimeOut(1));
        setters.put(29,                               unhandled);
        setters.put(30,                               unhandled);
        setters.put(31,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_LAST_ENDPOINT,       unhandled);
        setters.put(zmq.ZMQ.ZMQ_ROUTER_MANDATORY,    s -> filterdedConsumer(Sub.class, s, () -> s.setRouterMandatory(true)));
        setters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE,       s -> s.setTCPKeepAlive(1));
        setters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_CNT,   s -> s.setTCPKeepAliveCount(1L));
        setters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_IDLE,  s -> s.setTCPKeepAliveIdle(1L));
        setters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_INTVL, s -> s.setTCPKeepAliveInterval(1L));
        setters.put(38,                               unhandled);
        setters.put(39,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_IMMEDIATE,           s -> s.setImmediate(true));
        setters.put(zmq.ZMQ.ZMQ_XPUB_VERBOSE,        s -> filterdedConsumer(XPub.class, s, () -> s.setXpubVerbose(true)));
        setters.put(zmq.ZMQ.ZMQ_ROUTER_RAW,          s -> filterdedConsumer(Sub.class, s, () -> s.setRouterRaw(true)));
        setters.put(zmq.ZMQ.ZMQ_IPV6,                s -> s.setIPv6(true));
        setters.put(zmq.ZMQ.ZMQ_MECHANISM,           s -> s.setMechanism(new NullMechanism.NullMechanismSettings()));
        setters.put(zmq.ZMQ.ZMQ_PLAIN_SERVER,        unhandled); //s -> s.setPlainServer(true));
        setters.put(zmq.ZMQ.ZMQ_PLAIN_USERNAME,      unhandled); //s -> s.setPlainUsername(""));
        setters.put(zmq.ZMQ.ZMQ_PLAIN_PASSWORD,      unhandled); //s -> s.setPlainPassword(""));
        setters.put(zmq.ZMQ.ZMQ_CURVE_SERVER,        unhandled); //s -> s.setCurveServer(true));
        setters.put(zmq.ZMQ.ZMQ_CURVE_PUBLICKEY,     unhandled); //s -> s.setCurvePublicKey(new byte[32]));
        setters.put(zmq.ZMQ.ZMQ_CURVE_SECRETKEY,     unhandled); //s -> s.setCurveSecretKey(new byte[32]));
        setters.put(zmq.ZMQ.ZMQ_CURVE_SERVERKEY,     unhandled); //s -> s.setCurveServerKey(new byte[32]));
        setters.put(zmq.ZMQ.ZMQ_PROBE_ROUTER,        s -> filterdedConsumer(Sub.class, s, () -> s.setProbeRouter(true)));
        setters.put(zmq.ZMQ.ZMQ_REQ_CORRELATE,       s -> filterdedConsumer(Req.class, s, () -> s.setReqCorrelate(true)));
        setters.put(zmq.ZMQ.ZMQ_REQ_RELAXED,         s -> filterdedConsumer(Req.class, s, () -> s.setReqRelaxed(true)));
        setters.put(zmq.ZMQ.ZMQ_CONFLATE,            s -> s.setConflate(true));
        setters.put(zmq.ZMQ.ZMQ_ZAP_DOMAIN,          s -> s.setZapDomain(""));
        setters.put(zmq.ZMQ.ZMQ_ROUTER_HANDOVER,     s -> filterdedConsumer(Router.class, s, () -> s.setRouterMandatory(true)));
        setters.put(zmq.ZMQ.ZMQ_TOS,                 s -> s.setTos(1));
        setters.put(58,                               unhandled);
        setters.put(59,                               unhandled);
        setters.put(60,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_CONNECT_RID,         unhandled); //s -> filterdedConsumer(Router.class, s, () -> s.setConnectRid(new byte[0])));
        setters.put(zmq.ZMQ.ZMQ_GSSAPI_SERVER,       unhandled);
        setters.put(zmq.ZMQ.ZMQ_GSSAPI_PRINCIPAL,    unhandled);
        setters.put(zmq.ZMQ.ZMQ_GSSAPI_SERVICE_PRINCIPAL, unhandled);
        setters.put(zmq.ZMQ.ZMQ_GSSAPI_PLAINTEXT,    unhandled);
        setters.put(zmq.ZMQ.ZMQ_HANDSHAKE_IVL,       s -> s.setHandshakeIvl(1));
        setters.put(zmq.ZMQ.ZMQ_SOCKS_PROXY,         s -> s.setSocksProxy(""));
        setters.put(68,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_XPUB_NODROP,         s -> filterdedConsumer(XPub.class, s, () -> s.setXpubNoDrop(true)));
        setters.put(zmq.ZMQ.ZMQ_BLOCKY,              unhandled);
        setters.put(zmq.ZMQ.ZMQ_XPUB_MANUAL,         unhandled);
        setters.put(72,                               unhandled);
        setters.put(73,                               unhandled);
        setters.put(74,                               unhandled);
        setters.put(zmq.ZMQ.ZMQ_HEARTBEAT_IVL,       s -> s.setHeartbeatIvl(1));
        setters.put(zmq.ZMQ.ZMQ_HEARTBEAT_TTL,       s -> s.setHeartbeatTtl(1));
        setters.put(zmq.ZMQ.ZMQ_HEARTBEAT_TIMEOUT,   s -> s.setHeartbeatTimeout(1));
        setters.put(zmq.ZMQ.ZMQ_XPUB_VERBOSER,       s -> filterdedConsumer(XPub.class, s, () -> s.setXpubVerbose(true)));
        setters.put(zmq.ZMQ.ZMQ_HELLO_MSG,           s -> s.setHelloMsg(new byte[0]));
        setters.put(zmq.ZMQ.ZMQ_AS_TYPE,             unhandled);
        setters.put(zmq.ZMQ.ZMQ_DISCONNECT_MSG,      unhandled);
        setters.put(zmq.ZMQ.ZMQ_HICCUP_MSG,          unhandled);
        setters.put(zmq.ZMQ.ZMQ_SELFADDR_PROPERTY_NAME, unhandled);
        setters.put(zmq.ZMQ.ZMQ_ENCODER,             unhandled);
        setters.put(zmq.ZMQ.ZMQ_DECODER,             unhandled);
        setters.put(zmq.ZMQ.ZMQ_MSG_ALLOCATOR,       s -> s.setMsgAllocator(new MsgAllocatorHeap()));
        setters.put(zmq.ZMQ.ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD, s -> s.setMsgAllocationHeapThreshold(1));
        setters.put(zmq.ZMQ.ZMQ_HEARTBEAT_CONTEXT,   s -> s.setHeartbeatContext(new byte[0]));
        setters.put(1006, unhandled);
        setters.put(zmq.ZMQ.ZMQ_CHANNEL_WRAPPER_FACTORY, unhandled);
        setters.put(zmq.ZMQ.ZMQ_TLS_CONTEXT,         s -> s.setSslContext(sslContext));
        setters.put(zmq.ZMQ.ZMQ_TLS_PARAMETERS,      s -> s.setSslParameters(sslParams));
        setters.put(zmq.ZMQ.ZMQ_TLS_PRINCIPAL_CONVERT, s -> s.setPrincipalConvert(principalConverter));

        Map<Integer, Function<Socket, Object>> getters = new HashMap<>();
        getters.put(zmq.ZMQ.ZMQ_AFFINITY, Socket::getAffinity);
        getters.put(zmq.ZMQ.ZMQ_IDENTITY, Socket::getIdentity);
        getters.put(zmq.ZMQ.ZMQ_SUBSCRIBE, nothing);
        getters.put(zmq.ZMQ.ZMQ_UNSUBSCRIBE, nothing);
        getters.put(zmq.ZMQ.ZMQ_RATE, Socket::getRate);
        getters.put(zmq.ZMQ.ZMQ_RECOVERY_IVL, Socket::getRecoveryInterval);
        getters.put(10, nothing);
        getters.put(zmq.ZMQ.ZMQ_SNDBUF, Socket::getSendBufferSize);
        getters.put(zmq.ZMQ.ZMQ_RCVBUF, Socket::getReceiveBufferSize);
        getters.put(zmq.ZMQ.ZMQ_RCVMORE, nothing);
        getters.put(zmq.ZMQ.ZMQ_FD, Socket::getFD);
        getters.put(zmq.ZMQ.ZMQ_EVENTS, nothing);
        getters.put(zmq.ZMQ.ZMQ_TYPE, Socket::getType);
        getters.put(zmq.ZMQ.ZMQ_LINGER, Socket::getLinger);
        getters.put(zmq.ZMQ.ZMQ_RECONNECT_IVL, Socket::getReconnectIVL);
        getters.put(zmq.ZMQ.ZMQ_BACKLOG, Socket::getBacklog);
        getters.put(20, nothing);
        getters.put(zmq.ZMQ.ZMQ_RECONNECT_IVL_MAX, Socket::getReconnectIVLMax);
        getters.put(zmq.ZMQ.ZMQ_MAXMSGSIZE, Socket::getMaxMsgSize);
        getters.put(zmq.ZMQ.ZMQ_SNDHWM, Socket::getSndHWM);
        getters.put(zmq.ZMQ.ZMQ_RCVHWM, Socket::getRcvHWM);
        getters.put(zmq.ZMQ.ZMQ_MULTICAST_HOPS, nothing);
        getters.put(26, nothing);
        getters.put(zmq.ZMQ.ZMQ_RCVTIMEO, Socket::getReceiveTimeOut);
        getters.put(zmq.ZMQ.ZMQ_SNDTIMEO, Socket::getSendTimeOut);
        getters.put(29, nothing);
        getters.put(30, nothing);
        getters.put(31, nothing);
        getters.put(zmq.ZMQ.ZMQ_LAST_ENDPOINT, Socket::getLastEndpoint);
        getters.put(zmq.ZMQ.ZMQ_ROUTER_MANDATORY, nothing);
        getters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE, Socket::getTCPKeepAlive);
        getters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_CNT, Socket::getTCPKeepAliveCount);
        getters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_IDLE, Socket::getTCPKeepAliveIdle);
        getters.put(zmq.ZMQ.ZMQ_TCP_KEEPALIVE_INTVL, Socket::getTCPKeepAliveInterval);
        getters.put(38, nothing);
        getters.put(39, nothing);
        getters.put(zmq.ZMQ.ZMQ_XPUB_VERBOSE, nothing);
        getters.put(zmq.ZMQ.ZMQ_IMMEDIATE, Socket::isImmediate);
        getters.put(zmq.ZMQ.ZMQ_ROUTER_RAW, nothing);
        getters.put(zmq.ZMQ.ZMQ_IPV6, Socket::isIPv6);
        getters.put(zmq.ZMQ.ZMQ_MECHANISM, Socket::getMechanism);
        getters.put(zmq.ZMQ.ZMQ_PLAIN_SERVER, nothing);
        getters.put(zmq.ZMQ.ZMQ_PLAIN_USERNAME, nothing); //Socket::getPlainUsername);
        getters.put(zmq.ZMQ.ZMQ_PLAIN_PASSWORD, nothing); //Socket::getPlainPassword);
        getters.put(zmq.ZMQ.ZMQ_CURVE_SERVER, nothing);
        getters.put(zmq.ZMQ.ZMQ_CURVE_PUBLICKEY, nothing); //Socket::getCurvePublicKey);
        getters.put(zmq.ZMQ.ZMQ_CURVE_SECRETKEY, nothing); //Socket::getCurveSecretKey);
        getters.put(zmq.ZMQ.ZMQ_CURVE_SERVERKEY, nothing); //Socket::getCurveServerKey);
        getters.put(zmq.ZMQ.ZMQ_PROBE_ROUTER, nothing);
        getters.put(zmq.ZMQ.ZMQ_REQ_CORRELATE, nothing);
        getters.put(zmq.ZMQ.ZMQ_REQ_RELAXED, nothing);
        getters.put(zmq.ZMQ.ZMQ_CONFLATE, Socket::isConflate);
        getters.put(zmq.ZMQ.ZMQ_ZAP_DOMAIN, Socket::getZapDomain);
        getters.put(zmq.ZMQ.ZMQ_ROUTER_HANDOVER, nothing);
        getters.put(zmq.ZMQ.ZMQ_TOS, Socket::getTos);
        getters.put(58, nothing);
        getters.put(59, nothing);
        getters.put(60, nothing);
        getters.put(zmq.ZMQ.ZMQ_CONNECT_RID, nothing);
        getters.put(zmq.ZMQ.ZMQ_GSSAPI_SERVER, nothing);
        getters.put(zmq.ZMQ.ZMQ_GSSAPI_PRINCIPAL, nothing);
        getters.put(zmq.ZMQ.ZMQ_GSSAPI_SERVICE_PRINCIPAL, nothing);
        getters.put(zmq.ZMQ.ZMQ_GSSAPI_PLAINTEXT, nothing);
        getters.put(zmq.ZMQ.ZMQ_HANDSHAKE_IVL, Socket::getHandshakeIvl);
        getters.put(zmq.ZMQ.ZMQ_SOCKS_PROXY, Socket::getSocksProxy);
        getters.put(68, nothing);
        getters.put(zmq.ZMQ.ZMQ_XPUB_NODROP, nothing);
        getters.put(zmq.ZMQ.ZMQ_BLOCKY, nothing);
        getters.put(zmq.ZMQ.ZMQ_XPUB_MANUAL, nothing);
        getters.put(72, nothing);
        getters.put(73, nothing);
        getters.put(74, nothing);
        getters.put(zmq.ZMQ.ZMQ_HEARTBEAT_IVL, Socket::getHeartbeatIvl);
        getters.put(zmq.ZMQ.ZMQ_HEARTBEAT_TTL, Socket::getHeartbeatTtl);
        getters.put(zmq.ZMQ.ZMQ_HEARTBEAT_TIMEOUT, Socket::getHeartbeatTimeout);
        getters.put(zmq.ZMQ.ZMQ_XPUB_VERBOSER, Socket::getHeartbeatTimeout);
        getters.put(zmq.ZMQ.ZMQ_HELLO_MSG, nothing);
        getters.put(zmq.ZMQ.ZMQ_AS_TYPE, s -> s.getSocketType().type);
        getters.put(zmq.ZMQ.ZMQ_DISCONNECT_MSG, nothing);
        getters.put(zmq.ZMQ.ZMQ_HICCUP_MSG, nothing);
        getters.put(zmq.ZMQ.ZMQ_SELFADDR_PROPERTY_NAME, Socket::getSelfAddressPropertyName);
        getters.put(zmq.ZMQ.ZMQ_ENCODER, nothing);
        getters.put(zmq.ZMQ.ZMQ_DECODER, nothing);
        getters.put(zmq.ZMQ.ZMQ_MSG_ALLOCATOR, Socket::getMsgAllocator);
        getters.put(zmq.ZMQ.ZMQ_MSG_ALLOCATION_HEAP_THRESHOLD, Socket::getMsgAllocationHeapThreshold);
        getters.put(zmq.ZMQ.ZMQ_HEARTBEAT_CONTEXT, Socket::getHeartbeatContext);
        getters.put(1006, nothing);
        getters.put(zmq.ZMQ.ZMQ_CHANNEL_WRAPPER_FACTORY, Socket::getChannelWrapper);
        getters.put(zmq.ZMQ.ZMQ_TLS_CONTEXT, Socket::getSslContext);
        getters.put(zmq.ZMQ.ZMQ_TLS_PARAMETERS, Socket::getSslParameter);
        getters.put(zmq.ZMQ.ZMQ_TLS_PRINCIPAL_CONVERT, Socket::getPrincipalConverter);
        Set<Integer> returNull = Set.of(
                zmq.ZMQ.ZMQ_LAST_ENDPOINT
        );
        try (ZContext ctx = new ZContext(1);
                Socket aSocket = ctx.createSocket(SocketType.PULL);
        ) {
            IntConsumer tester = k -> {
                Assert.assertTrue("" + k, setters.containsKey(k));
                Assert.assertTrue("" + k, getters.containsKey(k));
                Function<Socket, Boolean> c = setters.get(k);
                if (c != unhandled) {
                    Assert.assertTrue("" + k, c.apply(aSocket));
                }
                Function<Socket, Object> g = getters.get(k);
                if (returNull.contains(k)) {
                    Assert.assertNull("" + k, g.apply(aSocket));
                }
                else if (g != nothing) {
                    Assert.assertNotNull("" + k, g.apply(aSocket));
                }
            };
            for (int i = 4; i < 83; i++) {
                tester.accept(i);
            }
            for (int i = 1001; i < 1009; i++) {
                tester.accept(i);
            }
        }
    }
}
