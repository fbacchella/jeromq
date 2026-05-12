package org.zeromq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

public class TestMethod
{
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    public void testGetSymbol()
    {
        assertEquals('-', Method.CONNECT.getSymbol());
        assertEquals('O', Method.BIND.getSymbol());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testActConnect()
    {
        try (Context context = new Context(1);
             Socket client = context.socket(SocketType.PAIR);
             Socket server = context.socket(SocketType.PAIR)) {
            int port = server.bindToRandomPort("tcp://127.0.0.1");
            Method.CONNECT.act(client, "tcp://127.0.0.1:" + port);

            client.send("hello");
            assertEquals("hello", server.recvStr());
            server.send("world");
            assertEquals("world", client.recvStr());
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testActBind()
    {
        try (Context context = new Context(1);
             Socket server = context.socket(SocketType.PAIR);
             Socket client = context.socket(SocketType.PAIR)) {
            int port = server.bindToRandomPort("tcp://127.0.0.1");
            String addr = "tcp://127.0.0.1:" + (port + 1);

            Method.BIND.act(server, addr);

            client.connect(addr);

            client.send("foo");
            assertEquals("foo", server.recvStr());
            server.send("bar");
            assertEquals("bar", client.recvStr());
        }
    }
}
