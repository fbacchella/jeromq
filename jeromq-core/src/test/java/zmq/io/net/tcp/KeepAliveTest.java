package zmq.io.net.tcp;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import jdk.net.ExtendedSocketOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeepAliveTest {

    @EnabledForJreRange(min = JRE.JAVA_13)
    @Test
    void testConsistent() throws IOException {
        SocketChannel sc = SocketChannel.open();
        TcpUtils.tuneTcpKeepalives(sc, 1, 2, 3, 4);

        assertEquals(2, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPCOUNT));
        assertEquals(3, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
        assertEquals(4, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPINTERVAL));

        ServerSocketChannel ssc = ServerSocketChannel.open();
        TcpUtils.tuneTcpKeepalives(ssc, 1, 2, 3, 4);

        assertEquals(2, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPCOUNT));
        assertEquals(3, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
        assertEquals(4, (int) sc.socket().getOption(ExtendedSocketOptions.TCP_KEEPINTERVAL));
    }
}
