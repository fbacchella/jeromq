package org.zeromq;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.util.Optional;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import tlschannel.SniSslContextFactory;
import zmq.Options;
import zmq.io.net.Address.IZAddress;
import zmq.io.net.SocketFactory;
import zmq.io.net.SocketFactory.ChannelFactoryWrapper;
import zmq.io.net.SocketWrapper;
import zmq.io.net.ServerSocketWrapper;
import zmq.util.Errno;

public class TlsChannelFactory extends SocketFactory implements
        ChannelFactoryWrapper<InetSocketAddress> {

    public static class Builder {
        private SSLContext ctx;
        private SSLParameters parameters;
        private SniSslContextFactory sniSslContextFactory;
        private Function<SSLSession, Optional<String>> principalConverter = s -> {
            try {
                return Optional.ofNullable(s.getPeerPrincipal().getName());
            } catch (SSLPeerUnverifiedException e) {
                return Optional.empty();
            }
        };
        public TlsChannelFactory.Builder setCtx(SSLContext ctx)
        {
            this.ctx = ctx;
            return this;
        }
        public TlsChannelFactory.Builder setParameters(SSLParameters parameters)
        {
            this.parameters = parameters;
            return this;
        }
        public TlsChannelFactory.Builder setSniSslContextFactory(SniSslContextFactory sniSslContextFactory)
        {
            this.sniSslContextFactory = sniSslContextFactory;
            return this;
        }
        public TlsChannelFactory.Builder setPrincipalConverter(Function<SSLSession, Optional<String>> principalConverter)
        {
            this.principalConverter = principalConverter;
            return this;
        }
        public TlsChannelFactory build()
        {
            return new TlsChannelFactory(this);
        }
    }
    public static Builder newBuilder()
    {
        return new Builder();
    }

    SocketFactory<InetSocketAddress> factory;

    private final SSLContext ctx;
    private final SSLParameters parameters;
    private final SniSslContextFactory sniSslContextFactory;
    private final Function<SSLSession, Optional<String>> principalConverter;

    private TlsChannelFactory(Builder builder)
    {
        ctx = builder.ctx;
        parameters = builder.parameters;
        sniSslContextFactory = builder.sniSslContextFactory;
        principalConverter = builder.principalConverter;
    }

    @Override
    public ServerSocketWrapper<InetSocketAddress> makeServerSocket(Options options) throws IOException
    {
        return new TlsServerSocketWrapper(factory.makeServerSocket(options), options.errno);
    }

    @Override
    public SocketWrapper<InetSocketAddress> makeSocket(Options options) throws IOException
    {
        TlsChannelWrapper.Builder builder = TlsChannelWrapper.newBuilder(factory.makeSocket(options), options.errno);
        builder.setAsServer(false)
               .setCtx(ctx)
               .setParameters(parameters)
               .setPrincipalConverter(principalConverter);
        return builder.build();
    }

    @Override
    public SocketFactory<InetSocketAddress> wrap(SocketFactory<InetSocketAddress> factory)
    {
        this.factory = factory;
        return this;
    }

    private class TlsServerSocketWrapper implements ServerSocketWrapper<InetSocketAddress>
    {
        private final ServerSocketWrapper<InetSocketAddress> parentSocket;
        private final Errno errno;
        private TlsServerSocketWrapper(ServerSocketWrapper<InetSocketAddress> parentSocket, Errno errno)
        {
            this.parentSocket = parentSocket;
            this.errno = errno;
        }

        @Override
        public SocketWrapper<InetSocketAddress> accept(IZAddress<InetSocketAddress> address) throws IOException
        {
            SocketWrapper<InetSocketAddress> wrapped = parentSocket.accept(address);
            TlsChannelWrapper.Builder builder = TlsChannelWrapper.newBuilder(wrapped, errno);
            builder.setAsServer(true)
                   .setCtx(ctx)
                   .setSniSslContextFactory(sniSslContextFactory)
                   .setPrincipalConverter(principalConverter)
                   .setParameters(parameters);
            return builder.build();
        }

        @Override
        public void close() throws IOException
        {
            parentSocket.close();
        }

        @Override
        public SelectableChannel getSelectableChannel()
        {
            return parentSocket.getSelectableChannel();
        }

        @Override
        public void configureBlocking(boolean b) throws IOException
        {
            parentSocket.configureBlocking(b);
        }

        @Override
        public void bind(InetSocketAddress socketAddress) throws IOException
        {
            parentSocket.bind(socketAddress);
        }

        @Override
        public InetSocketAddress getAddress() throws IOException
        {
            return parentSocket.getAddress();
        }
    }
}
