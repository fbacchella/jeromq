package zmq.io.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;

import zmq.Options;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.AbstractSocketConnecter;
import zmq.io.net.Address;
import zmq.io.net.SocketFactory;
import zmq.io.net.SocketWrapper;

public class TcpConnecter extends AbstractSocketConnecter<InetSocketAddress>
{
    public TcpConnecter(IOThread ioThread, SessionBase session, Options options, Address<InetSocketAddress> addr,
            boolean delayedStart)
    {
        super(ioThread, session, options, addr, delayedStart);
    }

    @Override
    protected SocketWrapper<InetSocketAddress> openClient(Address.IZAddress<InetSocketAddress> address) throws IOException
    {
        // Template resolution might need a little help sometimes
        SocketFactory<InetSocketAddress> f = options.getFactory();
        SocketWrapper<InetSocketAddress> fd = f.makeSocket(options);

        // Set the socket to non-blocking mode so that we get async connect().
        fd.configureBlocking(false);
        return fd;
    }

    @Override
    protected void tuneConnectedChannel(SocketWrapper<InetSocketAddress> channel) throws IOException
    {
        channel.tune();
    }
}
