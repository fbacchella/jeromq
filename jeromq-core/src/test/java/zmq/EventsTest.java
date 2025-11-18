package zmq;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.Events;
import org.zeromq.ProtocolCode;
import org.zeromq.ZEvent;
import org.zeromq.Errors;

public class EventsTest {

    @Test
    public void checkConsistency() throws IOException {
        Ctx ctx = new Ctx();
        SocketBase push = ctx.createSocket(ZMQ.ZMQ_PUSH);
        SocketBase pull = ctx.createSocket(ZMQ.ZMQ_PULL);
        ZMQ.bind(pull, "tcp://127.0.0.1:*");
        String host = (String) ZMQ.getSocketOptionExt(pull, ZMQ.ZMQ_LAST_ENDPOINT);
        ZMQ.connect(push, host);
        for (int i = 1; i <= zmq.ZMQ.ZMQ_EVENT_LAST; i *= 2) {
            Events ev = Events.findByCode(i);
            Assert.assertNotEquals(Events.ALL, ev);
            switch (ev) {
            case CONNECTED:
                withChannel(i, push, pull);
                break;
            case CONNECT_DELAYED:
                withError(i, push, pull);
                break;
            case CONNECT_RETRIED:
                withDuration(i, push, pull);
                break;
            case LISTENING:
                withChannel(i, push, pull);
                break;
            case BIND_FAILED:
                withError(i, push, pull);
                break;
            case ACCEPTED:
                withChannel(i, push, pull);
                break;
            case ACCEPT_FAILED:
                withError(i, push, pull);
                break;
            case CLOSED:
                withChannel(i, push, pull);
                break;
            case CLOSE_FAILED:
                withError(i, push, pull);
                break;
            case DISCONNECTED:
                withChannel(i, push, pull);
                break;
            case MONITOR_STOPPED:
                withNull(i, push, pull);
                break;
            case HANDSHAKE_FAILED_NO_DETAIL:
                withError(i, push, pull);
                break;
            case HANDSHAKE_SUCCEEDED:
                withError(i, push, pull);
                break;
            case HANDSHAKE_FAILED_PROTOCOL:
                withProtocolCode(i, push, pull);
                break;
            case HANDSHAKE_FAILED_AUTH:
                withInteger(i, push, pull);
                break;
            case HANDSHAKE_PROTOCOL:
                withInteger(i, push, pull);
                break;
            case EXCEPTION:
                withException(i, push, pull);
                break;
            default:
                Assert.fail("Missing " + ev);
                break;
            }
        }
    }

    public void withNull(int evCode, SocketBase push, SocketBase pull) {
        ZEvent zev = forward(evCode, null, push, pull);
        Assert.assertNull(zev.getValue());
    }

    public void withChannel(int evCode, SocketBase push, SocketBase pull) throws IOException {
        SocketChannel channel = SocketChannel.open();
        ZEvent zev = forward(evCode, channel, push, pull);
        Assert.assertEquals(channel, zev.getValue());
    }

    public void withDuration(int evCode, SocketBase push, SocketBase pull)
    {
        int duration = 1;
        ZEvent zev = forward(evCode, duration, push, pull);
        Assert.assertEquals(Duration.ofMillis(duration), zev.getValue());
    }

    public void withError(int evCode, SocketBase push, SocketBase pull)
    {
        Errors error = Errors.EINTR;
        ZEvent zev = forward(evCode, error.getCode(), push, pull);
        Assert.assertEquals(error, zev.getValue());
    }

    public void withException(int evCode, SocketBase push, SocketBase pull)
    {
        Throwable ex = new RuntimeException();
        ZEvent zev = forward(evCode, ex, push, pull);
        Assert.assertEquals(ex, zev.getValue());
    }

    public void withInteger(int evCode, SocketBase push, SocketBase pull)
    {
        Integer integer = 1;
        ZEvent zev = forward(evCode, integer, push, pull);
        Assert.assertEquals(integer, zev.getValue());
    }

    private void withProtocolCode(int evCode, SocketBase push, SocketBase pull)
    {
        ProtocolCode pc = ProtocolCode.ZMQ_PROTOCOL_ERROR_ZAP_BAD_REQUEST_ID;
        ZEvent zev = forward(evCode, pc.getCode(), push, pull);
        Assert.assertEquals(pc, zev.getValue());
    }

    private ZEvent forward(int eventCode, Object arg, SocketBase push, SocketBase pull)
    {
        ZMQ.Event ev = new ZMQ.Event(eventCode, "127.0.0.1:*", arg);
        Events evEnum = Events.findByCode(eventCode);
        ev.write(push);
        ZEvent zev = ZEvent.wrap(ZMQ.Event.read(pull));
        Assert.assertEquals(evEnum, zev.getEvent());
        Assert.assertEquals(eventCode, zev.getEvent().getCode());
        return zev;
    }

}
