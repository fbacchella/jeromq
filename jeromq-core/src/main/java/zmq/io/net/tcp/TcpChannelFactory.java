package zmq.io.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Locale;

import zmq.Options;
import zmq.ZError;
import zmq.io.net.Address;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.SocketFactory;
import zmq.io.net.ServerSocketWrapper;
import zmq.io.net.SocketWrapper;
import zmq.util.Utils;

public class TcpChannelFactory extends SocketFactory<InetSocketAddress> {
    private static final boolean isWindows;
    static {
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        isWindows = os.contains("win");
    }
    @Override
    public ServerSocketWrapper makeServerSocket(Options options) throws IOException {
        return new TcpServerSocket(options);
    }

    @Override
    public SocketWrapper<InetSocketAddress> makeSocket(Options options) throws IOException {
        SocketChannel channel = SocketChannel.open();
        //  Set the socket buffer limits for the underlying socket.
        if (options.sndbuf != 0) {
            channel.setOption(StandardSocketOptions.SO_SNDBUF, options.sndbuf);
        }
        if (options.rcvbuf != 0) {
            channel.setOption(StandardSocketOptions.SO_RCVBUF, options.rcvbuf);
        }

        // Set the IP Type-Of-Service priority for this socket
        if (options.tos != 0) {
            channel.setOption(StandardSocketOptions.IP_TOS, options.tos);
        }
        return new TcpSocket(options, channel);
    }

    private class TcpServerSocket implements ServerSocketWrapper<InetSocketAddress> {
        private final ServerSocketChannel serverChannel;
        private final Options options;
        public TcpServerSocket(Options options) throws IOException {
            this.serverChannel = ServerSocketChannel.open();
            this.options = options;
        }

        @Override
        public SocketWrapper<InetSocketAddress> accept(IZAddress<InetSocketAddress> address) throws IOException {
            SocketChannel sock = serverChannel.accept();
            if (!options.tcpAcceptFilters.isEmpty()) {
                boolean matched = false;
                for (TcpAddress.TcpAddressMask am : options.tcpAcceptFilters) {
                    if (am.matchAddress(address.address())) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    try {
                        sock.close();
                    }
                    catch (IOException ignored) {
                        // Ignored
                    }
                    return null;
                }
            }

            if (options.tos != 0) {
                sock.setOption(StandardSocketOptions.IP_TOS, options.tos);
            }
            //  Set the socket buffer limits for the underlying socket.
            if (options.sndbuf != 0) {
                sock.setOption(StandardSocketOptions.SO_SNDBUF, options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                sock.setOption(StandardSocketOptions.SO_RCVBUF, options.rcvbuf);
            }
            if (!isWindows) {
                sock.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            }
            return new TcpSocket(options, sock);
        }

        @Override
        public void close() throws IOException {
            serverChannel.close();
        }

        @Override
        public SelectableChannel getSelectableChannel() {
            return serverChannel;
        }

        @Override
        public void configureBlocking(boolean b) throws IOException {
            serverChannel.configureBlocking(b);
        }

        @Override
        public void bind(InetSocketAddress socketAddress) throws IOException {
            //  Set the socket buffer limits for the underlying socket.
            if (options.sndbuf != 0) {
                serverChannel.setOption(StandardSocketOptions.SO_SNDBUF, options.sndbuf);
            }
            if (options.rcvbuf != 0) {
                serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, options.rcvbuf);
            }
            if (!isWindows) {
                serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            }
            serverChannel.bind(socketAddress, options.backlog);
        }

        @Override
        public InetSocketAddress getAddress() throws IOException {
            return (InetSocketAddress) serverChannel.getLocalAddress();
        }
    }

    private class TcpSocket implements SocketWrapper<InetSocketAddress>
    {
        private final SocketChannel channel;
        private final Options options;

        private TcpSocket(Options options, SocketChannel channel)
        {
            this.channel = channel;
            this.options = options;
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
        public boolean connect(InetSocketAddress sa) throws IOException {
            return channel.connect(sa);
        }

        @Override
        public boolean finishConnect() throws IOException {
            return channel.finishConnect();
        }

        @Override
        public SocketChannel getNativeSocket() {
            return channel;
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public boolean isBlocking() {
            return channel.isBlocking();
        }

        @Override
        public void unblocking() throws IOException {
            channel.configureBlocking(false);
        }

        @Override
        public Address<InetSocketAddress> getPeerSocketAddress() {
            return Utils.getPeerSocketAddress(channel);
        }

        @Override
        public Address<InetSocketAddress> getLocalSocketAddress() {
            return Utils.getLocalSockedAddress(channel);
        }

        @Override
        public SelectableChannel getSelectableChannel() {
            return channel;
        }

        @Override
        public void configureBlocking(boolean b) throws IOException {
            channel.configureBlocking(b);
        }

        @Override
        public void tune() {
            try {
                channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                TcpUtils.tuneTcpKeepalives(channel, options.tcpKeepAlive, options.tcpKeepAliveCnt,
                        options.tcpKeepAliveIdle, options.tcpKeepAliveIntvl);
            } catch (IOException e) {
                throw new ZError.IOException(e);
            }
        }
    }

}
