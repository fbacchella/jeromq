package org.zeromq;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import zmq.io.mechanism.MechanismSettings;
import zmq.io.mechanism.curve.CurveMechanismSettings;
import zmq.io.mechanism.plain.PlainMechanismSettings;

import org.zeromq.ZMQ.Socket;

public class SocketConfigurator
{
    public final String endpoint;
    public final SocketType type;
    public final Method method;
    public final Integer sendHwm;
    public final Integer recvHwm;
    public final Long maxMsgSize;
    public final Integer linger;
    public final Integer backlog;
    public final Long affinity;
    public final ByteBuffer identity;
    public final Boolean ipv6;
    public final Integer receiveBufferSize;
    public final Integer sendBufferSize;
    public final Integer receiveTimeOut;
    public final Integer reconnectIVL;
    public final Integer reconnectIVLMax;
    public final Integer sendTimeOut;
    public final Integer tcpKeepAlive;
    public final Integer tcpKeepAliveCount;
    public final Integer tcpKeepAliveIdle;
    public final Integer tcpKeepAliveInterval;
    public final Boolean xpubVerbose;
    public final Integer tos;
    public final Integer heartbeatIvl;
    public final Integer heartbeatTimeout;
    public final Integer heartbeatTtl;
    public final ByteBuffer heartbeatContext;
    public final Integer handshakeIvl;
    public final Integer socksProxyPort;
    public final String socksProxyHost;
    public final Boolean xpubNoDrop;
    public final Boolean xpubManual;
    public final Boolean xpubVerboser;

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
        this.identity = builder.identity == null ? null : ByteBuffer.wrap(builder.identity).asReadOnlyBuffer();
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
        this.heartbeatContext = builder.heartbeatContext == null ? null : ByteBuffer.wrap(builder.heartbeatContext).asReadOnlyBuffer();
        this.handshakeIvl = builder.handshakeIvl;
        this.socksProxyPort = builder.socksProxyPort;
        this.socksProxyHost = builder.socksProxyHost;
        this.xpubNoDrop = builder.xpubNoDrop;
        this.xpubManual = builder.xpubManual;
        this.xpubVerboser = builder.xpubVerboser;

        this.mechanism = builder.mechanism;
    }

    public Socket getSocket(Socket socket)
    {
        Optional.of(maxMsgSize).stream().mapToLong(i -> i).filter(i -> i >= 0).forEach(socket::setMaxMsgSize);
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

        public Builder endpoint(String endpoint)
        {
            this.endpoint = endpoint;
            return this;
        }

        public Builder type(SocketType type)
        {
            this.type = type;
            return this;
        }

        public Builder method(Method method)
        {
            this.method = method;
            return this;
        }

        public Builder sendHwm(int sendHwm)
        {
            this.sendHwm = sendHwm;
            return this;
        }

        public Builder recvHwm(int recvHwm)
        {
            this.recvHwm = recvHwm;
            return this;
        }

        public Builder maxMsgSize(long maxMsgSize)
        {
            this.maxMsgSize = maxMsgSize;
            return this;
        }

        public Builder linger(int linger)
        {
            this.linger = linger;
            return this;
        }

        public Builder backlog(int backlog)
        {
            this.backlog = backlog;
            return this;
        }

        public Builder affinity(long affinity)
        {
            this.affinity = affinity;
            return this;
        }

        public Builder identity(byte[] identity)
        {
            this.identity = identity == null ? null : Arrays.copyOf(identity, identity.length);
            return this;
        }

        public Builder ipv6(boolean ipv6)
        {
            this.ipv6 = ipv6;
            return this;
        }

        public Builder receiveBufferSize(int receiveBufferSize)
        {
            this.receiveBufferSize = receiveBufferSize;
            return this;
        }

        public Builder sendBufferSize(int sendBufferSize)
        {
            this.sendBufferSize = sendBufferSize;
            return this;
        }

        public Builder receiveTimeOut(int receiveTimeOut)
        {
            this.receiveTimeOut = receiveTimeOut;
            return this;
        }

        public Builder reconnectIVL(int reconnectIVL)
        {
            this.reconnectIVL = reconnectIVL;
            return this;
        }

        public Builder reconnectIVLMax(int reconnectIVLMax)
        {
            this.reconnectIVLMax = reconnectIVLMax;
            return this;
        }

        public Builder sendTimeOut(int sendTimeOut)
        {
            this.sendTimeOut = sendTimeOut;
            return this;
        }

        public Builder tcpKeepAlive(int tcpKeepAlive)
        {
            this.tcpKeepAlive = tcpKeepAlive;
            return this;
        }

        public Builder tcpKeepAliveCount(int tcpKeepAliveCount)
        {
            this.tcpKeepAliveCount = tcpKeepAliveCount;
            return this;
        }

        public Builder tcpKeepAliveIdle(int tcpKeepAliveIdle)
        {
            this.tcpKeepAliveIdle = tcpKeepAliveIdle;
            return this;
        }

        public Builder tcpKeepAliveInterval(int tcpKeepAliveInterval)
        {
            this.tcpKeepAliveInterval = tcpKeepAliveInterval;
            return this;
        }

        public Builder xpubVerbose(boolean xpubVerbose)
        {
            this.xpubVerbose = xpubVerbose;
            return this;
        }

        public Builder tos(int tos)
        {
            this.tos = tos;
            return this;
        }

        public Builder heartbeatIvl(int heartbeatIvl)
        {
            this.heartbeatIvl = heartbeatIvl;
            return this;
        }

        public Builder heartbeatTimeout(int heartbeatTimeout)
        {
            this.heartbeatTimeout = heartbeatTimeout;
            return this;
        }

        public Builder heartbeatTtl(int heartbeatTtl)
        {
            this.heartbeatTtl = heartbeatTtl;
            return this;
        }

        public Builder heartbeatContext(byte[] heartbeatContext)
        {
            this.heartbeatContext = heartbeatContext == null ? null : Arrays.copyOf(heartbeatContext, heartbeatContext.length);
            return this;
        }

        public Builder handshakeIvl(int handshakeIvl)
        {
            this.handshakeIvl = handshakeIvl;
            return this;
        }

        public Builder socksProxyPort(int socksProxyPort)
        {
            this.socksProxyPort = socksProxyPort;
            return this;
        }

        public Builder socksProxyHost(String socksProxyHost)
        {
            this.socksProxyHost = socksProxyHost;
            return this;
        }

        public Builder xpubNoDrop(boolean xpubNoDrop)
        {
            this.xpubNoDrop = xpubNoDrop;
            return this;
        }

        public Builder xpubManual(boolean xpubManual)
        {
            this.xpubManual = xpubManual;
            return this;
        }

        public Builder xpubVerboser(boolean xpubVerboser)
        {
            this.xpubVerboser = xpubVerboser;
            return this;
        }

        public Builder plainUsername(String plainUsername)
        {
            this.plainUsername = plainUsername;
            return this;
        }

        public Builder plainPassword(String plainPassword)
        {
            this.plainPassword = plainPassword;
            return this;
        }

        public Builder curvePublicKey(Object curvePublicKey)
        {
            this.curvePublicKey = CurveMechanismSettings.curveKey(curvePublicKey);
            return this;
        }

        public Builder curveSecretKey(Object curveSecretKey)
        {
            this.curveSecretKey = CurveMechanismSettings.curveKey(curveSecretKey);
            return this;
        }

        public Builder curvePeerPublicKey(Object curvePeerPublicKey)
        {
            this.curvePeerPublicKey = CurveMechanismSettings.curveKey(curvePeerPublicKey);
            return this;
        }

        public Builder mechanism(MechanismSettings<?> mechanism)
        {
            this.mechanism = mechanism;
            return this;
        }

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

    public static Builder builder()
    {
        return new Builder();
    }

    public static SocketConfigurator build()
    {
        return builder().build();
    }

    public static SocketConfigurator from(Map<String, ?> settings)
    {
        Builder builder = builder();
        for (Map.Entry<String, ?> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            switch (key) {
                case "endpoint":
                    builder.endpoint((String) value);
                    break;
                case "type":
                    if (value instanceof SocketType) {
                        builder.type((SocketType) value);
                    }
                    else {
                        builder.type(SocketType.valueOf((String) value));
                    }
                    break;
                case "method":
                    if (value instanceof Method) {
                        builder.method((Method) value);
                    }
                    else {
                        builder.method(Method.valueOf((String) value));
                    }
                    break;
                case "sendHwm":
                    builder.sendHwm(((Number) value).intValue());
                    break;
                case "recvHwm":
                    builder.recvHwm(((Number) value).intValue());
                    break;
                case "maxMsgSize":
                    builder.maxMsgSize(((Number) value).longValue());
                    break;
                case "linger":
                    builder.linger(((Number) value).intValue());
                    break;
                case "backlog":
                    builder.backlog(((Number) value).intValue());
                    break;
                case "affinity":
                    builder.affinity(((Number) value).longValue());
                    break;
                case "identity":
                    if (value instanceof byte[]) {
                        builder.identity((byte[]) value);
                    }
                    else if (value instanceof ByteBuffer) {
                        ByteBuffer bb = ((ByteBuffer) value).asReadOnlyBuffer();
                        byte[] bytes = new byte[bb.remaining()];
                        bb.get(bytes);
                        builder.identity(bytes);
                    }
                    break;
                case "ipv6":
                    builder.ipv6((Boolean) value);
                    break;
                case "receiveBufferSize":
                    builder.receiveBufferSize(((Number) value).intValue());
                    break;
                case "sendBufferSize":
                    builder.sendBufferSize(((Number) value).intValue());
                    break;
                case "receiveTimeOut":
                    builder.receiveTimeOut(((Number) value).intValue());
                    break;
                case "reconnectIVL":
                    builder.reconnectIVL(((Number) value).intValue());
                    break;
                case "reconnectIVLMax":
                    builder.reconnectIVLMax(((Number) value).intValue());
                    break;
                case "sendTimeOut":
                    builder.sendTimeOut(((Number) value).intValue());
                    break;
                case "tcpKeepAlive":
                    builder.tcpKeepAlive(((Number) value).intValue());
                    break;
                case "tcpKeepAliveCount":
                    builder.tcpKeepAliveCount(((Number) value).intValue());
                    break;
                case "tcpKeepAliveIdle":
                    builder.tcpKeepAliveIdle(((Number) value).intValue());
                    break;
                case "tcpKeepAliveInterval":
                    builder.tcpKeepAliveInterval(((Number) value).intValue());
                    break;
                case "xpubVerbose":
                    builder.xpubVerbose((Boolean) value);
                    break;
                case "tos":
                    builder.tos(((Number) value).intValue());
                    break;
                case "heartbeatIvl":
                    builder.heartbeatIvl(((Number) value).intValue());
                    break;
                case "heartbeatTimeout":
                    builder.heartbeatTimeout(((Number) value).intValue());
                    break;
                case "heartbeatTtl":
                    builder.heartbeatTtl(((Number) value).intValue());
                    break;
                case "heartbeatContext":
                    if (value instanceof byte[]) {
                        builder.heartbeatContext((byte[]) value);
                    }
                    else if (value instanceof ByteBuffer) {
                        ByteBuffer bb = ((ByteBuffer) value).asReadOnlyBuffer();
                        byte[] bytes = new byte[bb.remaining()];
                        bb.get(bytes);
                        builder.heartbeatContext(bytes);
                    }
                    break;
                case "handshakeIvl":
                    builder.handshakeIvl(((Number) value).intValue());
                    break;
                case "socksProxyPort":
                    builder.socksProxyPort(((Number) value).intValue());
                    break;
                case "socksProxyHost":
                    builder.socksProxyHost((String) value);
                    break;
                case "xpubNoDrop":
                    builder.xpubNoDrop((Boolean) value);
                    break;
                case "xpubManual":
                    builder.xpubManual((Boolean) value);
                    break;
                case "xpubVerboser":
                    builder.xpubVerboser((Boolean) value);
                    break;
                case "plainUsername":
                    builder.plainUsername((String) value);
                    break;
                case "plainPassword":
                    builder.plainPassword((String) value);
                    break;
                case "curvePeerPublicKey":
                    builder.curvePeerPublicKey(value);
                    break;
                case "curvePublicKey":
                    builder.curvePublicKey(value);
                    break;
                case "curveSecretKey":
                    builder.curveSecretKey(value);
                    break;
                case "mechanism":
                    builder.mechanism((MechanismSettings<?>) value);
                    break;
                default:
                    assert false : "Unknown key " + key;
                    break;
            }
        }
        return builder.build();
    }
}
