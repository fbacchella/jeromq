package zmq.io.net.tipc;

import java.net.InetSocketAddress;

import zmq.Options;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address;
import zmq.io.net.tcp.TcpConnecter;

public class TipcConnecter extends TcpConnecter
{
    public TipcConnecter(IOThread ioThread, SessionBase session, Options options, Address<InetSocketAddress> addr,
            boolean wait)
    {
        super(ioThread, session, options, addr, wait);
        // TODO V4 implement Tipc
        throw new UnsupportedOperationException("TODO implement Tipc");
    }
}
