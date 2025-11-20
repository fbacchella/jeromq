package zmq.io.net.ipc;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import zmq.Options;
import zmq.io.net.Address;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.SocketFactory;
import zmq.io.net.SocketWrapper;
import zmq.io.net.ServerSocketWrapper;
import zmq.util.Utils;

public class IpcChannelFactory extends SocketFactory<UnixDomainSocketAddress> {

    @Override
    public ServerSocketWrapper makeServerSocket(Options options) throws IOException
    {
        return new IpcServerSocket(options);
    }

    @Override
    public SocketWrapper<UnixDomainSocketAddress> makeSocket(Options options) throws IOException
    {
        return new IpcSocket(options);
    }

    private class IpcServerSocket implements ServerSocketWrapper<UnixDomainSocketAddress>
    {
        private final ServerSocketChannel serverChannel;
        private final Options options;
        public IpcServerSocket(Options options) throws IOException {
            this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            this.options = options;
        }

        @Override
        public SocketWrapper<UnixDomainSocketAddress> accept(IZAddress<UnixDomainSocketAddress> address) throws IOException
        {
            SocketChannel sock = serverChannel.accept();
            //  Set the socket buffer limits for the underlying socket.
            if (options.sndbuf != 0) {
                sock.setOption(StandardSocketOptions.SO_SNDBUF, options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                sock.setOption(StandardSocketOptions.SO_RCVBUF, options.rcvbuf);
            }
            return new IpcSocket(options, sock);
        }

        @Override
        public void close() throws IOException
        {
            serverChannel.close();
        }

        @Override
        public SelectableChannel getSelectableChannel()
        {
            return serverChannel;
        }

        @Override
        public void configureBlocking(boolean b) throws IOException
        {
            serverChannel.configureBlocking(b);
        }

        @Override
        public void bind(UnixDomainSocketAddress socketAddress) throws IOException
        {
            //  Set the socket buffer limits for the underlying socket.
            if (options.rcvbuf != 0) {
                serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, options.rcvbuf);
            }
            serverChannel.bind(socketAddress, options.backlog);
        }

        @Override
        public UnixDomainSocketAddress getAddress() throws IOException
        {
            return (UnixDomainSocketAddress) serverChannel.getLocalAddress();
        }
    }

    class IpcSocket implements SocketWrapper<UnixDomainSocketAddress>
    {
        private final SocketChannel channel;

        private IpcSocket(Options options, SocketChannel channel) throws IOException
        {
            this.channel = channel;
            if (options.sndbuf != 0) {
                channel.setOption(StandardSocketOptions.SO_SNDBUF, options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                channel.setOption(StandardSocketOptions.SO_RCVBUF, options.rcvbuf);
            }
        }

        private IpcSocket(Options options) throws IOException
        {
            this(options, SocketChannel.open(StandardProtocolFamily.UNIX));
        }

        @Override
        public int write(ByteBuffer inBuffer) throws IOException
        {
            return channel.write(inBuffer);
        }

        @Override
        public int read(ByteBuffer outBuffer) throws IOException
        {
            return channel.read(outBuffer);
        }

        @Override
        public void close() throws IOException
        {
            channel.close();
        }

        @Override
        public boolean connect(UnixDomainSocketAddress sa) throws IOException
        {
            return channel.connect(sa);
        }

        @Override
        public boolean finishConnect() throws IOException
        {
            return channel.finishConnect();
        }

        @Override
        public SocketChannel getNativeSocket()
        {
            return channel;
        }

        @Override
        public boolean isOpen()
        {
            return channel.isOpen();
        }

        @Override
        public boolean isBlocking()
        {
            return channel.isBlocking();
        }

        @Override
        public void unblocking() throws IOException
        {
            channel.configureBlocking(false);
        }

        @Override
        public Address<UnixDomainSocketAddress> getPeerSocketAddress()
        {
            return Utils.getPeerSocketAddress(channel);
        }

        @Override
        public Address<UnixDomainSocketAddress> getLocalSocketAddress()
        {
            return Utils.getLocalSockedAddress(channel);
        }

        @Override
        public SelectableChannel getSelectableChannel()
        {
            return channel;
        }

        @Override
        public void configureBlocking(boolean b) throws IOException
        {
            channel.configureBlocking(b);
        }
        @Override
        public void tune()
        {

        }
    }

}
