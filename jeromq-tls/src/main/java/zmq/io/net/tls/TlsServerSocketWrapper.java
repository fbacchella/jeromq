package zmq.io.net.tls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.util.Optional;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import tlschannel.SniSslContextFactory;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.ServerSocketWrapper;
import zmq.io.net.SocketWrapper;
import zmq.util.Errno;

class TlsServerSocketWrapper implements ServerSocketWrapper<InetSocketAddress> {
    static class Builder {
        private final ServerSocketWrapper<InetSocketAddress> parentServerSocket;
        private Errno errno;
        private SSLContext ctx;
        private SSLParameters parameters;
        private SniSslContextFactory sniSslContextFactory;
        private PrincipalConverter principalConverter;

        private Builder(ServerSocketWrapper<InetSocketAddress> parentServerSocket)
        {
            this.parentServerSocket = parentServerSocket;
        }
        Builder setErrno(Errno errno)
        {
            this.errno = errno;
            return this;
        }
        Builder setCtx(SSLContext ctx)
        {
            this.ctx = ctx;
            return this;
        }
        Builder setParameters(SSLParameters parameters)
        {
            this.parameters = parameters;
            return this;
        }
        Builder setSniSslContextFactory(SniSslContextFactory sniSslContextFactory)
        {
            this.sniSslContextFactory = sniSslContextFactory;
            return this;
        }
        Builder setPrincipalConverter(PrincipalConverter principalConverter)
        {
            this.principalConverter = principalConverter;
            return this;
        }

        public ServerSocketWrapper<InetSocketAddress> build()
        {
            return new TlsServerSocketWrapper(this);
        }
    }
    public static Builder newBuilder(ServerSocketWrapper<InetSocketAddress> serverSocket)
    {
        return new Builder(serverSocket);
    }

    private final ServerSocketWrapper<InetSocketAddress> parentServerSocket;
    private final PrincipalConverter principalConverter;
    private final Errno errno;
    private final SSLContext sslContext;
    private final SSLParameters sslParams;
    private SniSslContextFactory sniSslContextFactory;

    TlsServerSocketWrapper(Builder builder) {
        assert builder.errno != null;
        this.parentServerSocket = builder.parentServerSocket;
        this.errno = builder.errno;
        this.principalConverter = builder.principalConverter;
        this.sslContext = builder.ctx;
        this.sslParams = builder.parameters;
        this.sniSslContextFactory = builder.sniSslContextFactory;
    }

    @Override
    public SocketWrapper<InetSocketAddress> accept(IZAddress<InetSocketAddress> address) throws IOException {
        SocketWrapper<InetSocketAddress> wrapped = parentServerSocket.accept(address);
        return TlsSocketWrapper.newBuilder(wrapped)
                               .setErrno(errno)
                               .setAsServer(true)
                               .setCtx(sslContext)
                               .setPrincipalConverter(principalConverter)
                               .setParameters(sslParams)
                               .setSniSslContextFactory(sniSslContextFactory)
                               .build();
    }

    @Override
    public void close() throws IOException {
        parentServerSocket.close();
    }

    @Override
    public SelectableChannel getSelectableChannel() {
        return parentServerSocket.getSelectableChannel();
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        parentServerSocket.configureBlocking(b);
    }

    @Override
    public void bind(InetSocketAddress socketAddress) throws IOException {
        parentServerSocket.bind(socketAddress);
    }

    @Override
    public InetSocketAddress getAddress() throws IOException {
        return parentServerSocket.getAddress();
    }
}
