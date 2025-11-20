package zmq.io.net.ipc;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;

import zmq.Options;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.AbstractSocketConnecter;
import zmq.io.net.Address;
import zmq.io.net.SocketFactory;
import zmq.io.net.SocketWrapper;

public class IpcConnecter extends AbstractSocketConnecter<UnixDomainSocketAddress>
{
    public IpcConnecter(IOThread ioThread, SessionBase session, Options options, Address<UnixDomainSocketAddress> addr, boolean wait)
    {
        super(ioThread, session, options, addr, wait);
    }

    @Override
    protected SocketWrapper<UnixDomainSocketAddress> openClient(Address.IZAddress<UnixDomainSocketAddress> address) throws IOException
    {
        // Template resolution might need a little help sometimes
        SocketFactory<UnixDomainSocketAddress> f = options.getFactory();
        SocketWrapper<UnixDomainSocketAddress> fd = f.makeSocket(options);
        fd.configureBlocking(false);
        return fd;
    }

    @Override
    protected void tuneConnectedChannel(SocketWrapper<UnixDomainSocketAddress> channel)
    {
        // no-op
    }
}
