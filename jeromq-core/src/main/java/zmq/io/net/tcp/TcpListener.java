package zmq.io.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;

import zmq.Options;
import zmq.SocketBase;
import zmq.io.IOThread;
import zmq.io.net.AbstractSocketListener;
import zmq.io.net.Address;
import zmq.io.net.SocketFactory;
import zmq.io.net.SocketWrapper;
import zmq.io.net.ServerSocketWrapper;

public class TcpListener extends AbstractSocketListener<InetSocketAddress, TcpAddress>
{

    public TcpListener(IOThread ioThread, SocketBase socket, Options options)
    {
        super(ioThread, socket, options);
    }

    @Override
    public String getAddress()
    {
        return address(getZAddress());
    }

    protected String address(Address.IZAddress<InetSocketAddress> address)
    {
        try {
            int port = getFd().getAddress().getPort();
            return address.toString(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean setAddress(String addr)
    {
        //  Convert the textual address into address structure.
        return setZAddress(new TcpAddress(addr, options.ipv6));
    }

    //  Set address to listen on, used by IpcListener that already resolved the address.
    protected boolean setAddress(InetSocketAddress addr)
    {
        //  Convert the textual address into address structure.
        return setZAddress(new TcpAddress(addr));
    }

    @Override
    protected ServerSocketWrapper<InetSocketAddress> openServer(TcpAddress address) throws IOException
    {
        // Template resolution might need a little help sometimes
        SocketFactory<InetSocketAddress> f = options.getFactory();
        return f.makeServerSocket(options);
    }

    @Override
    protected void bindServer(ServerSocketWrapper<InetSocketAddress> fd, TcpAddress address) throws IOException
    {

        fd.configureBlocking(false);
        fd.bind(address.address());
    }

    @Override
    protected SocketWrapper<InetSocketAddress> accept(ServerSocketWrapper<InetSocketAddress> fd) throws IOException
    {
        //  The situation where connection cannot be accepted due to insufficient
        //  resources is considered valid and treated by ignoring the connection.
        //  Accept one connection and deal with different failure modes.
        assert (fd != null);
        return fd.accept(getZAddress());
    }

    @Override
    protected void tuneAcceptedChannel(SocketWrapper<InetSocketAddress> channel) throws IOException
    {
        channel.tune();
    }

    @Override
    protected void closeServerChannel(ServerSocketWrapper<InetSocketAddress> fd) throws IOException
    {
        fd.close();
    }
}
