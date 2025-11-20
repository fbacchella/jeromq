package zmq.io.net.ipc;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.net.AbstractSocketListener;
import zmq.io.net.ServerSocketWrapper;
import zmq.io.net.SocketFactory;
import zmq.io.net.SocketWrapper;

public class IpcListener extends AbstractSocketListener<UnixDomainSocketAddress, IpcAddress>
{
    // bind will create this socket file but close will not remove it, so we need to do that ourselves on close.
    private Path boundSocketPath;

    public IpcListener(IOThread ioThread, SocketBase socket, Options options)
    {
        super(ioThread, socket, options);
    }

    @Override
    public String getAddress()
    {
        return getZAddress().toString(-1);
    }

    //  Set address to listen on.
    @Override
    public boolean setAddress(String addr)
    {
        return super.setZAddress(new IpcAddress(addr));
    }

    @Override
    protected ServerSocketWrapper<UnixDomainSocketAddress> openServer(IpcAddress address) throws IOException
    {
        // Template resolution might need a little help sometimes
        SocketFactory<UnixDomainSocketAddress> f = options.getFactory();
        return f.makeServerSocket(options);
    }

    @Override
    protected void bindServer(ServerSocketWrapper<UnixDomainSocketAddress> fd, IpcAddress address) throws IOException
    {
        fd.configureBlocking(false);
        fd.bind(address.address());
        assert (boundSocketPath == null);
        boundSocketPath = address.address().getPath();
    }

    @Override
    protected SocketWrapper<UnixDomainSocketAddress> accept(ServerSocketWrapper<UnixDomainSocketAddress> fd) throws IOException
    {
        return fd.accept(getZAddress());
    }

    @Override
    protected void tuneAcceptedChannel(SocketWrapper<UnixDomainSocketAddress> channel)
    {
        // no-op
    }

    @Override
    protected void closeServerChannel(ServerSocketWrapper<UnixDomainSocketAddress> fd) throws IOException
    {
        try {
            fd.close();
        }
        finally {
            Path socketPath = this.boundSocketPath;
            this.boundSocketPath = null;
            if (socketPath != null) {
                Files.deleteIfExists(socketPath);
            }
        }
    }
}
