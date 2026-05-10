package org.zeromq;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.proto.ZPicture;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestBinaryPicture
{
    @Test
    public void testSocketSendRecvBinaryPicture()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        String picture = "1248sScfm";

        ZPicture pic = new ZPicture();
        ZMsg msg = new ZMsg();
        msg.add("Hello");
        msg.add("World");
        rc = pic.sendBinaryPicture(
                                    push,
                                    picture,
                                    255,
                                    65535,
                                    429496729,
                                    Long.MAX_VALUE,
                                    "Hello World",
                                    "Hello cruel World!",
                                    "ABC".getBytes(ZMQ.CHARSET),
                                    new ZFrame("My frame"),
                                    msg);
        assertThat(rc, is(true));

        Object[] objects = pic.recvBinaryPicture(pull, picture);
        assertThat(objects[0], is(equalTo(255)));
        assertThat(objects[1], is(equalTo(65535)));
        assertThat(objects[2], is(equalTo(429496729)));
        assertThat(objects[3], is(equalTo(Long.MAX_VALUE)));
        assertThat(objects[4], is(equalTo("Hello World")));
        assertThat(objects[5], is(equalTo("Hello cruel World!")));
        assertThat(objects[6], is(equalTo("ABC".getBytes(zmq.ZMQ.CHARSET))));
        assertThat(objects[7], is(equalTo(new ZFrame("My frame"))));
        ZMsg expectedMsg = new ZMsg();
        expectedMsg.add("Hello");
        expectedMsg.add("World");
        assertThat(objects[8], is(equalTo(expectedMsg)));

        push.close();
        pull.close();
        context.term();
    }

    @Test
    public void testSocketSendRecvPicture()
    {
        Context context = ZMQ.context(1);

        Socket push = context.socket(SocketType.PUSH);
        Socket pull = context.socket(SocketType.PULL);

        boolean rc = pull.setReceiveTimeOut(50);
        assertThat(rc, is(true));
        int port = push.bindToRandomPort("tcp://127.0.0.1");
        rc = pull.connect("tcp://127.0.0.1:" + port);
        assertThat(rc, is(true));

        String picture = "1248sbfzm";

        ZPicture pic = new ZPicture();
        ZMsg msg = new ZMsg();
        msg.add("Hello");
        msg.add("World");
        rc = pic.sendPicture(
                              push,
                              picture,
                              255,
                              65535,
                              429496729,
                              Long.MAX_VALUE,
                              "Hello World",
                              "ABC".getBytes(ZMQ.CHARSET),
                              new ZFrame("My frame"),
                              msg);
        assertThat(rc, is(true));

        Object[] objects = pic.recvPicture(pull, picture);
        assertThat(objects[0], is(equalTo(255)));
        assertThat(objects[1], is(equalTo(65535)));
        assertThat(objects[2], is(equalTo(429496729)));
        assertThat(objects[3], is(equalTo(Long.MAX_VALUE)));
        assertThat(objects[4], is(equalTo("Hello World")));
        assertThat(objects[5], is(equalTo("ABC".getBytes(zmq.ZMQ.CHARSET))));
        assertThat(objects[6], is(equalTo(new ZFrame("My frame"))));
        assertThat(objects[7], is(equalTo(new ZFrame())));
        ZMsg expectedMsg = new ZMsg();
        expectedMsg.add("Hello");
        expectedMsg.add("World");
        assertThat(objects[8], is(equalTo(expectedMsg)));

        push.close();
        pull.close();
        context.term();
    }
}
