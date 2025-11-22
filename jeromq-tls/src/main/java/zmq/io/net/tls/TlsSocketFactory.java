package zmq.io.net.tls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;

import tlschannel.SniSslContextFactory;
import zmq.Options;
import zmq.ZMQ;
import zmq.io.net.ServerSocketWrapper;
import zmq.io.net.SocketFactory;
import zmq.io.net.SocketFactory.ChannelFactoryWrapper;
import zmq.io.net.SocketWrapper;

public class TlsSocketFactory extends SocketFactory<InetSocketAddress> implements
        ChannelFactoryWrapper<InetSocketAddress> {

    private static final SSLContext DEFAULT_SSL_CONTEXT;
    private static final SSLParameters DEFAULT_SSL_PARAMS;
    static {
        try {
            DEFAULT_SSL_CONTEXT = SSLContext.getDefault();
            DEFAULT_SSL_PARAMS = DEFAULT_SSL_CONTEXT.getDefaultSSLParameters();
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException("Miss configured TLS", e);
        }
    }

    public static class Builder {
        private SSLContext ctx = DEFAULT_SSL_CONTEXT;
        private SSLParameters parameters = DEFAULT_SSL_PARAMS;
        private SniSslContextFactory sniSslContextFactory;
        private PrincipalConverter principalConverter = s -> {
            try {
                return Optional.ofNullable(s.getPeerPrincipal().getName());
            } catch (SSLPeerUnverifiedException e) {
                return Optional.empty();
            }
        };
        public TlsSocketFactory.Builder setCtx(SSLContext ctx)
        {
            Optional.ofNullable(ctx).ifPresent(c -> this.ctx = c);
            return this;
        }
        public TlsSocketFactory.Builder setParameters(SSLParameters parameters)
        {
            Optional.ofNullable(parameters).ifPresent(p -> this.parameters = p);
            return this;
        }
        public TlsSocketFactory.Builder setSniSslContextFactory(SniSslContextFactory sniSslContextFactory)
        {
            this.sniSslContextFactory = sniSslContextFactory;
            return this;
        }
        public TlsSocketFactory.Builder setPrincipalConverter(PrincipalConverter principalConverter)
        {
            Optional.ofNullable(principalConverter).ifPresent(v -> this.principalConverter = v);
            return this;
        }
        public TlsSocketFactory build()
        {
            return new TlsSocketFactory(this);
        }
    }
    public static Builder newBuilder()
    {
        return new Builder();
    }

    SocketFactory<InetSocketAddress> factory;

    private final SSLContext sslContext;
    private final SSLParameters parameters;
    private final PrincipalConverter principalConverter;
    private final SniSslContextFactory sniSslContextFactory;

    private TlsSocketFactory(Builder builder)
    {
        sslContext = builder.ctx;
        parameters = builder.parameters;
        principalConverter = builder.principalConverter;
        sniSslContextFactory = builder.sniSslContextFactory;
    }

    @Override
    public ServerSocketWrapper<InetSocketAddress> makeServerSocket(Options options) throws IOException
    {
        ServerSocketWrapper<InetSocketAddress> parent = factory.makeServerSocket(options);
        return TlsServerSocketWrapper.newBuilder(parent)
                                     .setErrno(options.errno)
                                     .setCtx(resolve(options.sslContext, sslContext))
                                     .setParameters(resolve(options.sslParameters, parameters))
                                     .setPrincipalConverter(resolve(options.principalConverter, principalConverter))
                                     .setSniSslContextFactory(sniSslContextFactory)
                                     .build();
    }

    @Override
    public SocketWrapper<InetSocketAddress> makeSocket(Options options) throws IOException
    {
        SSLContext localctx = resolve(options.sslContext, sslContext);
        SSLParameters localparams = resolve(options.sslParameters, parameters);
        PrincipalConverter localconvert = resolve(options.principalConverter, principalConverter);
        options.setSocketOpt(ZMQ.ZMQ_TLS_CONTEXT, localctx);
        options.setSocketOpt(ZMQ.ZMQ_TLS_PARAMETERS, localparams);
        options.setSocketOpt(ZMQ.ZMQ_TLS_PRINCIPAL_CONVERT, localctx);
        return TlsSocketWrapper.newBuilder(factory.makeSocket(options))
                               .setAsServer(false)
                               .setErrno(options.errno)
                               .setCtx(localctx)
                               .setParameters(localparams)
                               .setPrincipalConverter(localconvert)
                               .build();
    }


    private <T> T resolve(T v1, T v2)
    {
        return Optional.ofNullable(v1).orElse(v2);
    }

    @Override
    public SocketFactory<InetSocketAddress> wrap(SocketFactory<InetSocketAddress> factory)
    {
        this.factory = factory;
        return this;
    }

}
