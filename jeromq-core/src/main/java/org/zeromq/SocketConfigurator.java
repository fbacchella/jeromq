package org.zeromq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.zeromq.ZMQ.Socket;

import zmq.io.mechanism.MechanismSettings;
import zmq.io.mechanism.curve.CurveMechanismSettings;
import zmq.io.mechanism.plain.PlainMechanismSettings;

/**
 * Immutable configuration holder for a ZeroMQ {@link ZMQ.Socket}.
 *
 * <p>A {@code SocketConfigurator} captures all the socket options that should be applied to a
 * socket before it is bound or connected. Instances are created through the fluent
 * {@link Builder} API and are fully immutable once built.</p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * SocketConfigurator cfg = SocketConfigurator.builder()
 *     .endpoint("tcp://localhost:5555")
 *     .type(SocketType.PUSH)
 *     .method(Method.CONNECT)
 *     .sendHwm(1000)
 *     .linger(0)
 *     .build();
 *
 * try (ZContext ctx = new ZContext()) {
 *     Socket socket = ctx.createSocket(cfg.type);
 *     cfg.getSocket(socket);
 *     socket.connect(cfg.endpoint);
 * }
 * }</pre>
 *
 * <p>A configurator can also be built from a plain {@link java.util.Map} of string keys to
 * values via {@link #from(Map)}.</p>
 *
 * @see Builder
 * @see ZMQ.Socket
 */
public class SocketConfigurator
{
    /** The endpoint URI (e.g. {@code "tcp://localhost:5555"}) to bind or connect to. */
    public final String endpoint;
    /** The ZeroMQ socket type (e.g. {@link SocketType#PUSH}, {@link SocketType#SUB}, …). */
    public final SocketType type;
    /** Whether the socket should {@link Method#BIND} or {@link Method#CONNECT} to the endpoint. */
    public final Method method;
    /** Send high-water mark: maximum number of outbound messages to queue before blocking/dropping. */
    public final Integer sendHwm;
    /** Receive high-water mark: maximum number of inbound messages to queue before blocking/dropping. */
    public final Integer recvHwm;
    /** Maximum message size in bytes; messages larger than this value are dropped. */
    public final Long maxMsgSize;
    /** Linger period in milliseconds: how long pending messages are kept after the socket is closed. */
    public final Integer linger;
    /** Maximum length of the queue of outstanding peer connections for a bound socket. */
    public final Integer backlog;
    /** I/O thread affinity bitmask that determines which I/O threads handle this socket's traffic. */
    public final Long affinity;
    /** Socket identity bytes used to route messages; stored as a read-only {@link ByteBuffer}. */
    public final ByteBuffer identity;
    /** Whether IPv6 is enabled on this socket. */
    public final Boolean ipv6;
    /** Kernel receive-buffer size in bytes (SO_RCVBUF). */
    public final Integer receiveBufferSize;
    /** Kernel send-buffer size in bytes (SO_SNDBUF). */
    public final Integer sendBufferSize;
    /** Receive timeout in milliseconds; {@code -1} means block indefinitely. */
    public final Integer receiveTimeOut;
    /** Initial reconnect interval in milliseconds before the socket retries a lost connection. */
    public final Integer reconnectIVL;
    /** Maximum reconnect interval in milliseconds; {@code 0} disables exponential back-off. */
    public final Integer reconnectIVLMax;
    /** Send timeout in milliseconds; {@code -1} means block indefinitely. */
    public final Integer sendTimeOut;
    /** TCP keep-alive mode: {@code -1} = OS default, {@code 0} = disabled, {@code 1} = enabled. */
    public final Integer tcpKeepAlive;
    /** Number of TCP keep-alive probes before the connection is considered dead. */
    public final Integer tcpKeepAliveCount;
    /** Idle time in seconds before TCP starts sending keep-alive probes. */
    public final Integer tcpKeepAliveIdle;
    /** Interval in seconds between individual TCP keep-alive probes. */
    public final Integer tcpKeepAliveInterval;
    /** When {@code true}, an XPUB socket forwards all subscription messages, including duplicates. */
    public final Boolean xpubVerbose;
    /** Type-of-Service (TOS) value set on outgoing IP packets. */
    public final Integer tos;
    /** Interval in milliseconds between ZMTP heartbeat {@code PING} messages. */
    public final Integer heartbeatIvl;
    /** Time in milliseconds to wait for a heartbeat {@code PONG} before considering the peer dead. */
    public final Integer heartbeatTimeout;
    /** Time-to-live in milliseconds that the remote peer should use for its own heartbeat timeout. */
    public final Integer heartbeatTtl;
    /** Opaque context bytes sent with heartbeat {@code PING} messages; stored as a read-only {@link ByteBuffer}. */
    public final ByteBuffer heartbeatContext;
    /** Maximum time in milliseconds to complete the ZMTP handshake before the connection is dropped. */
    public final Integer handshakeIvl;
    /** Port of the SOCKS5 proxy to route connections through. */
    public final Integer socksProxyPort;
    /** Hostname or IP address of the SOCKS5 proxy to route connections through. */
    public final String socksProxyHost;
    /** When {@code true}, an XPUB socket blocks on send instead of dropping messages when the HWM is reached. */
    public final Boolean xpubNoDrop;
    /** When {@code true}, an XPUB socket does not automatically send subscription messages to the application. */
    public final Boolean xpubManual;
    /** When {@code true}, an XPUB socket also forwards unsubscription messages (requires {@link #xpubVerbose}). */
    public final Boolean xpubVerboser;

    /** Security mechanism settings (PLAIN, CURVE, …) to apply to the socket; {@code null} means no security. */
    public final MechanismSettings<?> mechanism;

    private SocketConfigurator(Builder builder)
    {
        this.endpoint = builder.endpoint;
        this.type = builder.type;
        this.method = builder.method;
        this.sendHwm = builder.sendHwm;
        this.recvHwm = builder.recvHwm;
        this.maxMsgSize = builder.maxMsgSize;
        this.linger = builder.linger;
        this.backlog = builder.backlog;
        this.affinity = builder.affinity;
        this.identity = defensiveWrap(builder.identity);
        this.ipv6 = builder.ipv6;
        this.receiveBufferSize = builder.receiveBufferSize;
        this.sendBufferSize = builder.sendBufferSize;
        this.receiveTimeOut = builder.receiveTimeOut;
        this.reconnectIVL = builder.reconnectIVL;
        this.reconnectIVLMax = builder.reconnectIVLMax;
        this.sendTimeOut = builder.sendTimeOut;
        this.tcpKeepAlive = builder.tcpKeepAlive;
        this.tcpKeepAliveCount = builder.tcpKeepAliveCount;
        this.tcpKeepAliveIdle = builder.tcpKeepAliveIdle;
        this.tcpKeepAliveInterval = builder.tcpKeepAliveInterval;
        this.xpubVerbose = builder.xpubVerbose;
        this.tos = builder.tos;
        this.heartbeatIvl = builder.heartbeatIvl;
        this.heartbeatTimeout = builder.heartbeatTimeout;
        this.heartbeatTtl = builder.heartbeatTtl;
        this.heartbeatContext = defensiveWrap(builder.heartbeatContext);
        this.handshakeIvl = builder.handshakeIvl;
        this.socksProxyPort = builder.socksProxyPort;
        this.socksProxyHost = builder.socksProxyHost;
        this.xpubNoDrop = builder.xpubNoDrop;
        this.xpubManual = builder.xpubManual;
        this.xpubVerboser = builder.xpubVerboser;
        this.mechanism = builder.mechanism;
    }

    private ByteBuffer defensiveWrap(byte[] data)
    {
        if (data != null) {
            ByteBuffer buffer = ByteBuffer.allocate(data.length);
            return buffer.put(data).flip().asReadOnlyBuffer();
        }
        else {
            return ByteBuffer.allocate(0).asReadOnlyBuffer();
        }
    }

    /**
     * Applies all non-default options stored in this configurator to the given socket.
     *
     * <p>Only options whose value is non-{@code null} and satisfies the relevant threshold
     * (typically {@code >= 0}) are forwarded to the socket. The socket is returned for
     * convenient chaining.</p>
     *
     * @param socket the socket to configure; must not be {@code null}
     * @return the same {@code socket} instance, after all options have been applied
     */
    public Socket getSocket(Socket socket)
    {
        Optional.of(maxMsgSize).filter(i -> i >= 0).ifPresent(socket::setMaxMsgSize);
        Optional.of(linger).filter(i -> i > 0).ifPresent(socket::setLinger);
        Optional.of(backlog).filter(i -> i >= 0).ifPresent(socket::setBacklog);
        Optional.of(affinity).filter(i -> i >= 0).ifPresent(socket::setAffinity);
        Optional.of(tcpKeepAlive).filter(i -> i >= 0).ifPresent(socket::setTCPKeepAlive);
        Optional.of(tcpKeepAliveCount).filter(i -> i >= 0).ifPresent(socket::setTCPKeepAliveCount);
        Optional.of(tcpKeepAliveIdle).filter(i -> i >= 0).ifPresent(socket::setTCPKeepAliveIdle);
        Optional.of(recvHwm).filter(i -> i >= 0).ifPresent(socket::setRcvHWM);
        Optional.of(sendHwm).filter(i -> i >= 0).ifPresent(socket::setSndHWM);
        Optional.of(tos).filter(i -> i >= 0).ifPresent(socket::setTos);
        Optional.of(sendBufferSize).filter(i -> i >= 0).ifPresent(socket::setSendBufferSize);
        Optional.of(receiveBufferSize).filter(i -> i >= 0).ifPresent(socket::setReceiveBufferSize);
        Optional.of(sendTimeOut).filter(i -> i >= 0).ifPresent(socket::setSendTimeOut);
        Optional.of(receiveTimeOut).filter(i -> i >= 0).ifPresent(socket::setReceiveTimeOut);

        if (identity != null && identity.hasRemaining()) {
            byte[] identityBytes = new byte[identity.remaining()];
            identity.duplicate().get(identityBytes);
            socket.setIdentity(identityBytes);
        }
        else if (endpoint != null && type != null && method != null) {
            String url = endpoint + ":" + type + ":" + method.getSymbol();
            socket.setIdentity(url.getBytes());
        }

        Optional.of(reconnectIVL).filter(i -> i >= 0).ifPresent(socket::setReconnectIVL);
        Optional.of(reconnectIVLMax).filter(i -> i >= 0).ifPresent(socket::setReconnectIVLMax);
        Optional.of(tcpKeepAliveInterval).filter(i -> i >= 0).ifPresent(socket::setTCPKeepAliveInterval);
        Optional.of(heartbeatIvl).filter(i -> i >= 0).ifPresent(socket::setHeartbeatIvl);
        Optional.of(heartbeatTimeout).filter(i -> i >= 0).ifPresent(socket::setHeartbeatTimeout);
        Optional.of(heartbeatTtl).filter(i -> i >= 0).ifPresent(socket::setHeartbeatTtl);
        if (heartbeatContext != null) {
            byte[] hbBytes = new byte[heartbeatContext.remaining()];
            heartbeatContext.duplicate().get(hbBytes);
            socket.setHeartbeatContext(hbBytes);
        }
        Optional.of(handshakeIvl).filter(i -> i >= 0).ifPresent(socket::setHandshakeIvl);
        if (socksProxyHost != null && socksProxyPort > 0) {
            socket.setSocksProxy(socksProxyHost + ":" + socksProxyPort);
        }
        socket.setXpubVerbose(xpubVerbose);
        socket.setXpubNoDrop(xpubNoDrop);
        socket.setXpubManual(xpubManual);
        socket.setXpubVerboser(xpubVerboser);
        socket.setIPv6(ipv6);
        Optional.ofNullable(mechanism).ifPresent(socket::setMechanism);
        return socket;
    }

    /**
     * Fluent builder for {@link SocketConfigurator}.
     *
     * <p>All setter methods return {@code this} to allow method chaining. Call {@link #build()}
     * to obtain an immutable {@link SocketConfigurator} instance.</p>
     */
    public static class Builder
    {
        private String endpoint;
        private SocketType type;
        private Method method;
        private Integer sendHwm;
        private Integer recvHwm;
        private Long maxMsgSize;
        private Integer linger;
        private Integer backlog;
        private Long affinity;
        private byte[] identity;
        private Boolean ipv6;
        private Integer receiveBufferSize;
        private Integer sendBufferSize;
        private Integer receiveTimeOut;
        private Integer reconnectIVL;
        private Integer reconnectIVLMax;
        private Integer sendTimeOut;
        private Integer tcpKeepAlive;
        private Integer tcpKeepAliveCount;
        private Integer tcpKeepAliveIdle;
        private Integer tcpKeepAliveInterval;
        private Boolean xpubVerbose;
        private Integer tos;
        private Integer heartbeatIvl;
        private Integer heartbeatTimeout;
        private Integer heartbeatTtl;
        private byte[] heartbeatContext;
        private Integer handshakeIvl;
        private Integer socksProxyPort;
        private String socksProxyHost;
        private Boolean xpubNoDrop;
        private Boolean xpubManual;
        private Boolean xpubVerboser;

        private String plainUsername;
        private String plainPassword;
        private byte[] curvePublicKey;
        private byte[] curveSecretKey;
        private byte[] curvePeerPublicKey;

        private MechanismSettings<?> mechanism;

        /**
         * Sets the endpoint URI (e.g. {@code "tcp://localhost:5555"}).
         *
         * @param endpoint the endpoint URI
         * @return this builder
         */
        public Builder endpoint(String endpoint)
        {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the ZeroMQ socket type.
         *
         * @param type the socket type
         * @return this builder
         */
        public Builder type(SocketType type)
        {
            this.type = type;
            return this;
        }

        /**
         * Sets whether the socket should bind or connect to the endpoint.
         *
         * @param method {@link Method#BIND} or {@link Method#CONNECT}
         * @return this builder
         */
        public Builder method(Method method)
        {
            this.method = method;
            return this;
        }

        /**
         * Sets the send high-water mark.
         *
         * @param sendHwm maximum number of outbound messages to queue
         * @return this builder
         */
        public Builder sendHwm(int sendHwm)
        {
            this.sendHwm = sendHwm;
            return this;
        }

        /**
         * Sets the receive high-water mark.
         *
         * @param recvHwm maximum number of inbound messages to queue
         * @return this builder
         */
        public Builder recvHwm(int recvHwm)
        {
            this.recvHwm = recvHwm;
            return this;
        }

        /**
         * Sets the maximum message size in bytes.
         *
         * @param maxMsgSize maximum message size; messages larger than this are dropped
         * @return this builder
         */
        public Builder maxMsgSize(long maxMsgSize)
        {
            this.maxMsgSize = maxMsgSize;
            return this;
        }

        /**
         * Sets the linger period in milliseconds.
         *
         * @param linger how long pending messages are kept after the socket is closed
         * @return this builder
         */
        public Builder linger(int linger)
        {
            this.linger = linger;
            return this;
        }

        /**
         * Sets the maximum length of the queue of outstanding peer connections.
         *
         * @param backlog maximum number of pending connections
         * @return this builder
         */
        public Builder backlog(int backlog)
        {
            this.backlog = backlog;
            return this;
        }

        /**
         * Sets the I/O thread affinity bitmask.
         *
         * @param affinity bitmask of I/O threads that handle this socket's traffic
         * @return this builder
         */
        public Builder affinity(long affinity)
        {
            this.affinity = affinity;
            return this;
        }

        /**
         * Sets the socket identity bytes used for message routing.
         *
         * @param identity raw identity bytes
         * @return this builder
         */
        public Builder identity(byte[] identity)
        {
            this.identity = identity;
            return this;
        }

        /**
         * Enables or disables IPv6 on the socket.
         *
         * @param ipv6 {@code true} to enable IPv6
         * @return this builder
         */
        public Builder ipv6(boolean ipv6)
        {
            this.ipv6 = ipv6;
            return this;
        }

        /**
         * Sets the kernel receive-buffer size (SO_RCVBUF) in bytes.
         *
         * @param receiveBufferSize receive buffer size in bytes
         * @return this builder
         */
        public Builder receiveBufferSize(int receiveBufferSize)
        {
            this.receiveBufferSize = receiveBufferSize;
            return this;
        }

        /**
         * Sets the kernel send-buffer size (SO_SNDBUF) in bytes.
         *
         * @param sendBufferSize send buffer size in bytes
         * @return this builder
         */
        public Builder sendBufferSize(int sendBufferSize)
        {
            this.sendBufferSize = sendBufferSize;
            return this;
        }

        /**
         * Sets the receive timeout in milliseconds.
         *
         * @param receiveTimeOut timeout in milliseconds; {@code -1} blocks indefinitely
         * @return this builder
         */
        public Builder receiveTimeOut(int receiveTimeOut)
        {
            this.receiveTimeOut = receiveTimeOut;
            return this;
        }

        /**
         * Sets the initial reconnect interval in milliseconds.
         *
         * @param reconnectIVL initial interval before retrying a lost connection
         * @return this builder
         */
        public Builder reconnectIVL(int reconnectIVL)
        {
            this.reconnectIVL = reconnectIVL;
            return this;
        }

        /**
         * Sets the maximum reconnect interval in milliseconds.
         *
         * @param reconnectIVLMax maximum interval; {@code 0} disables exponential back-off
         * @return this builder
         */
        public Builder reconnectIVLMax(int reconnectIVLMax)
        {
            this.reconnectIVLMax = reconnectIVLMax;
            return this;
        }

        /**
         * Sets the send timeout in milliseconds.
         *
         * @param sendTimeOut timeout in milliseconds; {@code -1} blocks indefinitely
         * @return this builder
         */
        public Builder sendTimeOut(int sendTimeOut)
        {
            this.sendTimeOut = sendTimeOut;
            return this;
        }

        /**
         * Sets the TCP keep-alive mode.
         *
         * @param tcpKeepAlive {@code -1} = OS default, {@code 0} = disabled, {@code 1} = enabled
         * @return this builder
         */
        public Builder tcpKeepAlive(int tcpKeepAlive)
        {
            this.tcpKeepAlive = tcpKeepAlive;
            return this;
        }

        /**
         * Sets the number of TCP keep-alive probes before the connection is considered dead.
         *
         * @param tcpKeepAliveCount number of probes
         * @return this builder
         */
        public Builder tcpKeepAliveCount(int tcpKeepAliveCount)
        {
            this.tcpKeepAliveCount = tcpKeepAliveCount;
            return this;
        }

        /**
         * Sets the idle time in seconds before TCP starts sending keep-alive probes.
         *
         * @param tcpKeepAliveIdle idle time in seconds
         * @return this builder
         */
        public Builder tcpKeepAliveIdle(int tcpKeepAliveIdle)
        {
            this.tcpKeepAliveIdle = tcpKeepAliveIdle;
            return this;
        }

        /**
         * Sets the interval in seconds between individual TCP keep-alive probes.
         *
         * @param tcpKeepAliveInterval interval in seconds
         * @return this builder
         */
        public Builder tcpKeepAliveInterval(int tcpKeepAliveInterval)
        {
            this.tcpKeepAliveInterval = tcpKeepAliveInterval;
            return this;
        }

        /**
         * Enables or disables verbose mode on an XPUB socket.
         *
         * <p>When enabled, all subscription messages (including duplicates) are forwarded.</p>
         *
         * @param xpubVerbose {@code true} to enable verbose mode
         * @return this builder
         */
        public Builder xpubVerbose(boolean xpubVerbose)
        {
            this.xpubVerbose = xpubVerbose;
            return this;
        }

        /**
         * Sets the Type-of-Service value on outgoing IP packets.
         *
         * @param tos TOS value
         * @return this builder
         */
        public Builder tos(int tos)
        {
            this.tos = tos;
            return this;
        }

        /**
         * Sets the interval in milliseconds between ZMTP heartbeat PING messages.
         *
         * @param heartbeatIvl interval in milliseconds; {@code 0} disables heartbeats
         * @return this builder
         */
        public Builder heartbeatIvl(int heartbeatIvl)
        {
            this.heartbeatIvl = heartbeatIvl;
            return this;
        }

        /**
         * Sets the time in milliseconds to wait for a heartbeat PONG before considering the peer dead.
         *
         * @param heartbeatTimeout timeout in milliseconds
         * @return this builder
         */
        public Builder heartbeatTimeout(int heartbeatTimeout)
        {
            this.heartbeatTimeout = heartbeatTimeout;
            return this;
        }

        /**
         * Sets the time-to-live in milliseconds that the remote peer should use for its own heartbeat timeout.
         *
         * @param heartbeatTtl TTL in milliseconds
         * @return this builder
         */
        public Builder heartbeatTtl(int heartbeatTtl)
        {
            this.heartbeatTtl = heartbeatTtl;
            return this;
        }

        /**
         * Sets the opaque context bytes sent with heartbeat PING messages.
         *
         * @param heartbeatContext context bytes
         * @return this builder
         */
        public Builder heartbeatContext(byte[] heartbeatContext)
        {
            this.heartbeatContext = heartbeatContext;
            return this;
        }

        /**
         * Sets the maximum time in milliseconds to complete the ZMTP handshake.
         *
         * @param handshakeIvl maximum handshake duration in milliseconds
         * @return this builder
         */
        public Builder handshakeIvl(int handshakeIvl)
        {
            this.handshakeIvl = handshakeIvl;
            return this;
        }

        /**
         * Sets the port of the SOCKS5 proxy.
         *
         * @param socksProxyPort proxy port number
         * @return this builder
         */
        public Builder socksProxyPort(int socksProxyPort)
        {
            this.socksProxyPort = socksProxyPort;
            return this;
        }

        /**
         * Sets the hostname or IP address of the SOCKS5 proxy.
         *
         * @param socksProxyHost proxy hostname or IP address
         * @return this builder
         */
        public Builder socksProxyHost(String socksProxyHost)
        {
            this.socksProxyHost = socksProxyHost;
            return this;
        }

        /**
         * Enables or disables the no-drop mode on an XPUB socket.
         *
         * <p>When enabled, the socket blocks on send instead of dropping messages when the HWM is reached.</p>
         *
         * @param xpubNoDrop {@code true} to block instead of dropping
         * @return this builder
         */
        public Builder xpubNoDrop(boolean xpubNoDrop)
        {
            this.xpubNoDrop = xpubNoDrop;
            return this;
        }

        /**
         * Enables or disables manual subscription mode on an XPUB socket.
         *
         * <p>When enabled, the socket does not automatically forward subscription messages to the application.</p>
         *
         * @param xpubManual {@code true} to enable manual mode
         * @return this builder
         */
        public Builder xpubManual(boolean xpubManual)
        {
            this.xpubManual = xpubManual;
            return this;
        }

        /**
         * Enables or disables verboser mode on an XPUB socket.
         *
         * <p>When enabled, unsubscription messages are also forwarded (requires verbose mode).</p>
         *
         * @param xpubVerboser {@code true} to enable verboser mode
         * @return this builder
         */
        public Builder xpubVerboser(boolean xpubVerboser)
        {
            this.xpubVerboser = xpubVerboser;
            return this;
        }

        /**
         * Sets the username for PLAIN authentication.
         *
         * @param plainUsername the PLAIN username
         * @return this builder
         */
        public Builder plainUsername(String plainUsername)
        {
            this.plainUsername = plainUsername;
            return this;
        }

        /**
         * Sets the password for PLAIN authentication.
         *
         * @param plainPassword the PLAIN password
         * @return this builder
         */
        public Builder plainPassword(String plainPassword)
        {
            this.plainPassword = plainPassword;
            return this;
        }

        /**
         * Sets the CURVE public key.
         *
         * <p>The key can be provided as a {@code byte[]}, a {@link java.nio.ByteBuffer}, or a
         * Z85-encoded {@link String}.</p>
         *
         * @param curvePublicKey the CURVE public key
         * @return this builder
         */
        public Builder curvePublicKey(byte[] curvePublicKey)
        {
            this.curvePublicKey = curvePublicKey;
            return this;
        }

        /**
         * Sets the CURVE secret key.
         *
         * @param curveSecretKey the CURVE secret key
         * @return this builder
         */
        public Builder curveSecretKey(byte[] curveSecretKey)
        {
            this.curveSecretKey = curveSecretKey;
            return this;
        }

        /**
         * Sets the CURVE peer (server) public key.
         *
         * @param curvePeerPublicKey the CURVE peer public key
         * @return this builder
         */
        public Builder curvePeerPublicKey(byte[] curvePeerPublicKey)
        {
            this.curvePeerPublicKey = curvePeerPublicKey;
            return this;
        }

        /**
         * Sets a pre-built security mechanism configuration.
         *
         * <p>When set, this takes precedence over any individual PLAIN or CURVE key settings.</p>
         *
         * @param mechanism the security mechanism settings
         * @return this builder
         */
        public Builder mechanism(MechanismSettings<?> mechanism)
        {
            this.mechanism = mechanism;
            return this;
        }

        /**
         * Builds and returns an immutable {@link SocketConfigurator}.
         *
         * <p>If no explicit {@link MechanismSettings} was provided, the method will automatically
         * create a {@link zmq.io.mechanism.plain.PlainMechanismSettings} when both
         * {@code plainUsername} and {@code plainPassword} are set, or a
         * {@link zmq.io.mechanism.curve.CurveMechanismSettings} when a CURVE secret key is set.</p>
         *
         * @return a new {@link SocketConfigurator}
         */
        public SocketConfigurator build()
        {
            if (mechanism == null) {
                if (plainUsername != null && plainPassword != null) {
                    mechanism = new PlainMechanismSettings(false, plainUsername, plainPassword);
                }
                else if (curveSecretKey != null) {
                    CurveMechanismSettings.Builder curveBuilder = CurveMechanismSettings.getBuilder();
                    curveBuilder.setSecretKey(curveSecretKey);
                    if (curvePublicKey != null) {
                        curveBuilder.setPublicKey(curvePublicKey);
                    }
                    if (curvePeerPublicKey != null) {
                        curveBuilder.setCurvePeerPublicKey(curvePeerPublicKey);
                    }
                    // Note: curveServer in SocketConfigurator was a boolean.
                    // In CurveMechanismSettings, server role is inferred if serverKey is null.
                    mechanism = curveBuilder.build();
                }
            }
            return new SocketConfigurator(this);
        }
    }

    private static byte[] bytearrayFrom(Object value)
    {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        else if (value instanceof ByteBuffer) {
            ByteBuffer bb = ((ByteBuffer) value).asReadOnlyBuffer();
            byte[] bytes = new byte[bb.remaining()];
            bb.get(bytes);
            return bytes;
        }
        else if (value instanceof String) {
            return EncodingDetector.decode((String) value);
        }
        else {
            return value.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static Number numberFrom(Object value)
    {
        if (value instanceof Number) {
            return (Number) value;
        }
        else {
            try {
                return Long.parseLong(value.toString());
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a number: \"" + value + "\"");
            }
        }
    }

    private static Boolean booleanFrom(Object value)
    {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        else {
            switch (value.toString().toLowerCase(Locale.ENGLISH)) {
            case "1":
            case "yes":
            case "true":
                return true;
            case "0":
            case "no":
            case "false":
                return false;
            default:
                throw new IllegalArgumentException("Not a boolean: \"" + value + "\"");
            }
        }
    }

    /**
     * Creates a new {@link Builder} with all options unset.
     *
     * @return a new builder instance
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Creates a {@link SocketConfigurator} with all options at their default (unset) values.
     *
     * <p>Equivalent to {@code builder().build()}.</p>
     *
     * @return a default {@link SocketConfigurator}
     */
    public static SocketConfigurator build()
    {
        return builder().build();
    }

    /**
     * Creates a {@link SocketConfigurator} from a map of string keys to values.
     *
     * <p>Each map key corresponds to a {@link Builder} setter name (e.g. {@code "endpoint"},
     * {@code "sendHwm"}, {@code "ipv6"}, …). Values are coerced to the expected type:
     * numeric strings are parsed as numbers, {@code "true"}/{@code "false"}/{@code "yes"}/
     * {@code "no"}/{@code "1"}/{@code "0"} are accepted as booleans, and byte-array fields
     * accept {@code byte[]}, {@link java.nio.ByteBuffer}, or Base64/Z85-encoded strings.</p>
     *
     * @param settings map of option names to values; {@code null} values are silently ignored
     * @return a new {@link SocketConfigurator} configured from the map
     * @throws IllegalArgumentException if a value cannot be coerced to the expected type
     */
    public static SocketConfigurator from(Map<String, ?> settings)
    {
        Builder builder = builder();
        for (Map.Entry<String, ?> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            try {
                switch (key) {
                case "endpoint":
                    builder.endpoint((String) value);
                    break;
                case "type":
                    if (value instanceof SocketType) {
                        builder.type((SocketType) value);
                    }
                    else {
                        builder.type(SocketType.valueOf(value.toString().toUpperCase(Locale.ENGLISH)));
                    }
                    break;
                case "method":
                    if (value instanceof Method) {
                        builder.method((Method) value);
                    }
                    else {
                        builder.method(Method.valueOf(value.toString().toUpperCase(Locale.ENGLISH)));
                    }
                    break;
                case "sendHwm":
                    builder.sendHwm(numberFrom(value).intValue());
                    break;
                case "recvHwm":
                    builder.recvHwm(numberFrom(value).intValue());
                    break;
                case "maxMsgSize":
                    builder.maxMsgSize(numberFrom(value).longValue());
                    break;
                case "linger":
                    builder.linger(numberFrom(value).intValue());
                    break;
                case "backlog":
                    builder.backlog(numberFrom(value).intValue());
                    break;
                case "affinity":
                    builder.affinity(numberFrom(value).longValue());
                    break;
                case "identity":
                    builder.identity(bytearrayFrom(value));
                    break;
                case "ipv6":
                    builder.ipv6(booleanFrom(value));
                    break;
                case "receiveBufferSize":
                    builder.receiveBufferSize(numberFrom(value).intValue());
                    break;
                case "sendBufferSize":
                    builder.sendBufferSize(numberFrom(value).intValue());
                    break;
                case "receiveTimeOut":
                    builder.receiveTimeOut(numberFrom(value).intValue());
                    break;
                case "reconnectIVL":
                    builder.reconnectIVL(numberFrom(value).intValue());
                    break;
                case "reconnectIVLMax":
                    builder.reconnectIVLMax(numberFrom(value).intValue());
                    break;
                case "sendTimeOut":
                    builder.sendTimeOut(numberFrom(value).intValue());
                    break;
                case "tcpKeepAlive":
                    builder.tcpKeepAlive(numberFrom(value).intValue());
                    break;
                case "tcpKeepAliveCount":
                    builder.tcpKeepAliveCount(numberFrom(value).intValue());
                    break;
                case "tcpKeepAliveIdle":
                    builder.tcpKeepAliveIdle(numberFrom(value).intValue());
                    break;
                case "tcpKeepAliveInterval":
                    builder.tcpKeepAliveInterval(numberFrom(value).intValue());
                    break;
                case "xpubVerbose":
                    builder.xpubVerbose(booleanFrom(value));
                    break;
                case "tos":
                    builder.tos(numberFrom(value).intValue());
                    break;
                case "heartbeatIvl":
                    builder.heartbeatIvl(numberFrom(value).intValue());
                    break;
                case "heartbeatTimeout":
                    builder.heartbeatTimeout(numberFrom(value).intValue());
                    break;
                case "heartbeatTtl":
                    builder.heartbeatTtl(numberFrom(value).intValue());
                    break;
                case "heartbeatContext":
                    builder.heartbeatContext(bytearrayFrom(value));
                    break;
                case "handshakeIvl":
                    builder.handshakeIvl(numberFrom(value).intValue());
                    break;
                case "socksProxyPort":
                    builder.socksProxyPort(numberFrom(value).intValue());
                    break;
                case "socksProxyHost":
                    builder.socksProxyHost(value.toString());
                    break;
                case "xpubNoDrop":
                    builder.xpubNoDrop(booleanFrom(value));
                    break;
                case "xpubManual":
                    builder.xpubManual(booleanFrom(value));
                    break;
                case "xpubVerboser":
                    builder.xpubVerboser(booleanFrom(value));
                    break;
                case "plainUsername":
                    builder.plainUsername(value.toString());
                    break;
                case "plainPassword":
                    builder.plainPassword(value.toString());
                    break;
                case "curvePeerPublicKey":
                    builder.curvePeerPublicKey(CurveMechanismSettings.curveKey(value));
                    break;
                case "curvePublicKey":
                    builder.curvePublicKey(CurveMechanismSettings.curveKey(value));
                    break;
                case "curveSecretKey":
                    builder.curveSecretKey(CurveMechanismSettings.curveKey(value));
                    break;
                case "mechanism":
                    builder.mechanism((MechanismSettings<?>) value);
                    break;
                default:
                    assert false : "Unknown key " + key;
                    break;
                }
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid value for key '" + key + "': " + e.getMessage(), e);
            }
        }
        return builder.build();
    }
}
