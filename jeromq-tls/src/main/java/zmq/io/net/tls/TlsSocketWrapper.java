package zmq.io.net.tls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import tlschannel.ClientTlsChannel;
import tlschannel.NeedsReadException;
import tlschannel.NeedsWriteException;
import tlschannel.ServerTlsChannel;
import tlschannel.SniSslContextFactory;
import tlschannel.TlsChannel;
import zmq.ZError;
import zmq.io.Metadata;
import zmq.io.net.Address;
import zmq.io.net.SocketWrapper;
import zmq.util.Errno;

class TlsSocketWrapper implements SocketWrapper<InetSocketAddress>
{
    static class Builder {
        private final SocketWrapper<InetSocketAddress> rawSocket;
        private Errno errno;
        private SSLContext ctx;
        private boolean asServer;
        private SSLParameters parameters;
        private SniSslContextFactory sniSslContextFactory;
        private PrincipalConverter principalConverter;
        private Builder(SocketWrapper<InetSocketAddress> rawSocket)
        {
            this.rawSocket = rawSocket;
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
        Builder setAsServer(boolean asServer)
        {
            this.asServer = asServer;
            if (! asServer) {
                this.sniSslContextFactory = null;
            }
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
            this.asServer = true;
            return this;
        }
        Builder setPrincipalConverter(PrincipalConverter principalConverter)
        {
            this.principalConverter = principalConverter;
            return this;
        }

        TlsSocketWrapper build()
        {
            return new TlsSocketWrapper(this);
        }
    }
    public static Builder newBuilder(SocketWrapper<InetSocketAddress> channel)
    {
        return new Builder(channel);
    }

    private final SocketWrapper<InetSocketAddress> rawChannel;
    private final TlsChannel tlsChannel;
    private final PrincipalConverter principalConverter;
    private final Errno errno;

    private TlsSocketWrapper(Builder builder)
    {
        assert builder.errno != null;
        if (builder.asServer) {
            tlsChannel = buildServer(builder.rawSocket, builder.ctx, builder.sniSslContextFactory, builder.parameters);
        } else {
            tlsChannel = buildClient(builder.rawSocket, builder.ctx, builder.parameters);
        }
        this.rawChannel = builder.rawSocket;
        this.principalConverter = builder.principalConverter;
        this.errno = builder.errno;
    }

    TlsChannel buildServer(ByteChannel channel, SSLContext ctx, SniSslContextFactory sslContextFactory, SSLParameters sslParameters)
    {
        ServerTlsChannel.Builder builder;
        if (sslContextFactory != null) {
            builder = ServerTlsChannel.newBuilder(channel, sslContextFactory);
        }
        else {
            builder = ServerTlsChannel.newBuilder(channel, ctx);
        }
        if (sslParameters != null) {
            builder.withEngineFactory(c -> {
                SSLEngine engine = c.createSSLEngine();
                engine.setUseClientMode(false);
                engine.setSSLParameters(sslParameters);
                return engine;
            });
        }
        return builder.withWaitForCloseConfirmation(false).build();
    }

    TlsChannel buildClient(ByteChannel channel, SSLContext ctx, SSLParameters params)
    {
        TlsChannel newChannel = ClientTlsChannel.newBuilder(channel, ctx).withWaitForCloseConfirmation(false).build();
        if (params != null) {
            newChannel.getSslEngine().setSSLParameters(params);
        }
        return newChannel;
    }

    @Override
    public int write(ByteBuffer inBuffer) throws IOException
    {
        try {
            return tlsChannel.write(inBuffer);
        } catch (SSLException ex) {
            Logger.getLogger(getClass().getName()).warning("write " + ex.getMessage());
            errno.set(ZError.ENOTSUP, ex);
            return -1;
        } catch (NeedsReadException | NeedsWriteException ex) {
            // Because of handshake, both read and write exceptions can be thrown
            return 0;
        }
    }

    @Override
    public int read(ByteBuffer outBuffer) throws IOException
    {
        try {
            return tlsChannel.read(outBuffer);
        } catch (SSLException ex) {
            Logger.getLogger(getClass().getName()).warning("write " + ex.getMessage());
            errno.set(ZError.ENOTSUP, ex);
            return -1;
        } catch (NeedsReadException | NeedsWriteException ex) {
            // Because of handshake, both read and write exceptions can be thrown
            return 0;
        }
    }

    @Override
    public void resolveMetadata(Metadata metadata)
    {
        principalConverter.getPrincipal(tlsChannel.getSslEngine().getSession())
                          .ifPresent(s -> metadata.put(Metadata.USER_ID, s));
        SocketWrapper.super.resolveMetadata(metadata);
    }

    @Override
    public boolean connect(InetSocketAddress sa) throws IOException
    {
        return rawChannel.connect(sa);
    }

    @Override
    public boolean finishConnect() throws IOException
    {
        return rawChannel.finishConnect();
    }

    @Override
    public SocketChannel getNativeSocket()
    {
        return rawChannel.getNativeSocket();
    }

    @Override
    public boolean isOpen()
    {
        return tlsChannel.isOpen();
    }

    @Override
    public void unblocking() throws IOException
    {
        rawChannel.configureBlocking(false);
    }

    @Override
    public Address<InetSocketAddress> getPeerSocketAddress()
    {
        return rawChannel.getPeerSocketAddress();
    }

    @Override
    public Address<InetSocketAddress> getLocalSocketAddress()
    {
        return rawChannel.getLocalSocketAddress();
    }

    @Override
    public SelectableChannel getSelectableChannel()
    {
        return rawChannel.getSelectableChannel();
    }

    @Override
    public void tune()
    {

    }

    @Override
    public void configureBlocking(boolean b) throws IOException
    {
        rawChannel.configureBlocking(b);
    }

    @Override
    public boolean isBlocking()
    {
        return rawChannel.isBlocking();
    }

    @Override
    public void close() throws IOException
    {
        tlsChannel.close();
    }
}
