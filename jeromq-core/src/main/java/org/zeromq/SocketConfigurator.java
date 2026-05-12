package org.zeromq;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.zeromq.ZMQ.Socket;

public class SocketConfigurator {

    public static final SocketType DEFAULT_TYPE = SocketType.PUB;
    public static final Method DEFAULT_METHOD = Method.CONNECT;

    public final String endpoint;
    public final SocketType type;
    public final Method method;
    public final int sendHwm;
    public final int recvHwm;
    public final long maxMsgSize;
    public final int linger;
    public final String peerPublicKey;
    public final String privateKeyFile;
    public final String publicKey;
    public final boolean autoCreate;
    public final int backlog;
    public final long affinity;
    public final byte[] identity;
    public final boolean ipv6;
    public final int receiveBufferSize;
    public final int sendBufferSize;
    public final int receiveTimeOut;
    public final int reconnectIVL;
    public final int reconnectIVLMax;
    public final int sendTimeOut;
    public final int tcpKeepAlive;
    public final int tcpKeepAliveCount;
    public final int tcpKeepAliveIdle;
    public final int tcpKeepAliveInterval;
    public final boolean xpubVerbose;
    public final int tos;
    public final int heartbeatIvl;
    public final int heartbeatTimeout;
    public final int heartbeatTtl;
    public final byte[] heartbeatContext;
    public final int handshakeIvl;
    public final int socksProxyPort;
    public final String socksProxyHost;
    public final boolean xpubNoDrop;
    public final boolean xpubManual;
    public final boolean xpubVerboser;


    private SocketConfigurator(Builder builder) {
        this.endpoint = builder.endpoint;
        this.type = builder.type;
        this.method = builder.method;
        this.sendHwm = builder.sendHwm;
        this.recvHwm = builder.recvHwm;
        this.maxMsgSize = builder.maxMsgSize;
        this.linger = builder.linger;
        this.peerPublicKey = builder.peerPublicKey;
        this.privateKeyFile = builder.privateKeyFile;
        this.publicKey = builder.publicKey;
        this.autoCreate = builder.autoCreate;
        this.backlog = builder.backlog;
        this.affinity = builder.affinity;
        this.identity = builder.identity;
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
        this.heartbeatContext = builder.heartbeatContext;
        this.handshakeIvl = builder.handshakeIvl;
        this.socksProxyPort = builder.socksProxyPort;
        this.socksProxyHost = builder.socksProxyHost;
        this.xpubNoDrop = builder.xpubNoDrop;
        this.xpubManual = builder.xpubManual;
        this.xpubVerboser = builder.xpubVerboser;
    }

    public Socket getSocket(ZContext ctx) {
        Socket socket = ctx.createSocket(type);
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

        if (identity != null && identity.length > 0) {
            socket.setIdentity(identity);
        } else {
            String url = endpoint + ":" + type.toString() + ":" + method.getSymbol();
            socket.setIdentity(url.getBytes());
        }

        Optional.of(reconnectIVL).filter(i -> i >= 0).ifPresent(socket::setReconnectIVL);
        Optional.of(reconnectIVLMax).filter(i -> i >= 0).ifPresent(socket::setReconnectIVLMax);
        Optional.of(tcpKeepAliveInterval).filter(i -> i >= 0).ifPresent(socket::setTCPKeepAliveInterval);
        Optional.of(heartbeatIvl).filter(i -> i >= 0).ifPresent(socket::setHeartbeatIvl);
        Optional.of(heartbeatTimeout).filter(i -> i >= 0).ifPresent(socket::setHeartbeatTimeout);
        Optional.of(heartbeatTtl).filter(i -> i >= 0).ifPresent(socket::setHeartbeatTtl);
        Optional.ofNullable(heartbeatContext).ifPresent(socket::setHeartbeatContext);
        Optional.of(handshakeIvl).filter(i -> i >= 0).ifPresent(socket::setHandshakeIvl);
        if (socksProxyHost != null && socksProxyPort > 0) {
            socket.setSocksProxy(socksProxyHost + ":" + socksProxyPort);
        }
        socket.setXpubVerbose(xpubVerbose);
        socket.setXpubNoDrop(xpubNoDrop);
        socket.setXpubManual(xpubManual);
        socket.setXpubVerboser(xpubVerboser);
        socket.setIPv6(ipv6);
        method.act(socket, endpoint);
        return socket;
    }

    public static class Builder {
        private String endpoint;
        private SocketType type = DEFAULT_TYPE;
        private Method method = DEFAULT_METHOD;
        private int sendHwm = zmq.ZMQ.DEFAULT_SEND_HWM;
        private int recvHwm = zmq.ZMQ.DEFAULT_RECV_HWM;
        private long maxMsgSize = zmq.ZMQ.DEFAULT_MAX_MSG_SIZE;
        private int linger = zmq.ZMQ.DEFAULT_LINGER;
        private String peerPublicKey;
        private String privateKeyFile;
        private String publicKey;
        private boolean autoCreate = false;
        private int backlog = zmq.ZMQ.DEFAULT_BACKLOG;
        private long affinity = zmq.ZMQ.DEFAULT_AFFINITY;
        private byte[] identity = zmq.ZMQ.DEFAULT_IDENTITY;
        private boolean ipv6 = zmq.ZMQ.DEFAULT_IPV6;
        private int receiveBufferSize = zmq.ZMQ.DEFAULT_RCVBUF;
        private int sendBufferSize = zmq.ZMQ.DEFAULT_SNDBUF;
        private int receiveTimeOut = zmq.ZMQ.DEFAULT_RECV_TIMEOUT;
        private int reconnectIVL = zmq.ZMQ.DEFAULT_RECONNECT_IVL;
        private int reconnectIVLMax = zmq.ZMQ.DEFAULT_RECONNECT_IVL_MAX;
        private int sendTimeOut = zmq.ZMQ.DEFAULT_SEND_TIMEOUT;
        private int tcpKeepAlive = zmq.ZMQ.DEFAULT_TCP_KEEP_ALIVE;
        private int tcpKeepAliveCount = zmq.ZMQ.DEFAULT_TCP_KEEP_ALIVE_CNT;
        private int tcpKeepAliveIdle = zmq.ZMQ.DEFAULT_TCP_KEEP_ALIVE_IDLE;
        private int tcpKeepAliveInterval = zmq.ZMQ.DEFAULT_TCP_KEEP_ALIVE;
        private boolean xpubVerbose = false;
        private int tos = zmq.ZMQ.DEFAULT_TOS;
        private int heartbeatIvl = 0;
        private int heartbeatTimeout = 0;
        private int heartbeatTtl = 0;
        private byte[] heartbeatContext = null;
        private int handshakeIvl = 0;
        private int socksProxyPort = 0;
        private String socksProxyHost = null;
        private boolean xpubNoDrop = false;
        private boolean xpubManual = false;
        private boolean xpubVerboser = false;

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder type(SocketType type) {
            this.type = type;
            return this;
        }

        public Builder method(Method method) {
            this.method = method;
            return this;
        }

        public Builder sendHwm(int sendHwm) {
            this.sendHwm = sendHwm;
            return this;
        }

        public Builder recvHwm(int recvHwm) {
            this.recvHwm = recvHwm;
            return this;
        }

        public Builder maxMsgSize(long maxMsgSize) {
            this.maxMsgSize = maxMsgSize;
            return this;
        }

        public Builder linger(int linger) {
            this.linger = linger;
            return this;
        }

        public Builder peerPublicKey(String peerPublicKey) {
            this.peerPublicKey = peerPublicKey;
            return this;
        }

        public Builder privateKeyFile(String privateKeyFile) {
            this.privateKeyFile = privateKeyFile;
            return this;
        }

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder autoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public Builder backlog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        public Builder affinity(long affinity) {
            this.affinity = affinity;
            return this;
        }

        public Builder identity(byte[] identity) {
            this.identity = identity == null ? null : Arrays.copyOf(identity, identity.length);
            return this;
        }

        public Builder ipv6(boolean ipv6) {
            this.ipv6 = ipv6;
            return this;
        }

        public Builder receiveBufferSize(int receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
            return this;
        }

        public Builder sendBufferSize(int sendBufferSize) {
            this.sendBufferSize = sendBufferSize;
            return this;
        }

        public Builder receiveTimeOut(int receiveTimeOut) {
            this.receiveTimeOut = receiveTimeOut;
            return this;
        }

        public Builder reconnectIVL(int reconnectIVL) {
            this.reconnectIVL = reconnectIVL;
            return this;
        }

        public Builder reconnectIVLMax(int reconnectIVLMax) {
            this.reconnectIVLMax = reconnectIVLMax;
            return this;
        }

        public Builder sendTimeOut(int sendTimeOut) {
            this.sendTimeOut = sendTimeOut;
            return this;
        }

        public Builder tcpKeepAlive(int tcpKeepAlive) {
            this.tcpKeepAlive = tcpKeepAlive;
            return this;
        }

        public Builder tcpKeepAliveCount(int tcpKeepAliveCount) {
            this.tcpKeepAliveCount = tcpKeepAliveCount;
            return this;
        }

        public Builder tcpKeepAliveIdle(int tcpKeepAliveIdle) {
            this.tcpKeepAliveIdle = tcpKeepAliveIdle;
            return this;
        }

        public Builder tcpKeepAliveInterval(int tcpKeepAliveInterval) {
            this.tcpKeepAliveInterval = tcpKeepAliveInterval;
            return this;
        }

        public Builder xpubVerbose(boolean xpubVerbose) {
            this.xpubVerbose = xpubVerbose;
            return this;
        }

        public Builder tos(int tos) {
            this.tos = tos;
            return this;
        }

        public Builder heartbeatIvl(int heartbeatIvl) {
            this.heartbeatIvl = heartbeatIvl;
            return this;
        }

        public Builder heartbeatTimeout(int heartbeatTimeout) {
            this.heartbeatTimeout = heartbeatTimeout;
            return this;
        }

        public Builder heartbeatTtl(int heartbeatTtl) {
            this.heartbeatTtl = heartbeatTtl;
            return this;
        }

        public Builder heartbeatContext(byte[] heartbeatContext) {
            this.heartbeatContext = heartbeatContext == null ? null : Arrays.copyOf(heartbeatContext, heartbeatContext.length);
            return this;
        }

        public Builder handshakeIvl(int handshakeIvl) {
            this.handshakeIvl = handshakeIvl;
            return this;
        }

        public Builder socksProxyPort(int socksProxyPort) {
            this.socksProxyPort = socksProxyPort;
            return this;
        }

        public Builder socksProxyHost(String socksProxyHost) {
            this.socksProxyHost = socksProxyHost;
            return this;
        }

        public Builder xpubNoDrop(boolean xpubNoDrop) {
            this.xpubNoDrop = xpubNoDrop;
            return this;
        }

        public Builder xpubManual(boolean xpubManual) {
            this.xpubManual = xpubManual;
            return this;
        }

        public Builder xpubVerboser(boolean xpubVerboser) {
            this.xpubVerboser = xpubVerboser;
            return this;
        }

        public SocketConfigurator build() {
            return new SocketConfigurator(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SocketConfigurator build() {
        return builder().build();
    }

    public static SocketConfigurator from(Map<String, ?> settings) {
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
                    } else {
                        builder.type(SocketType.valueOf((String) value));
                    }
                    break;
                case "method":
                    if (value instanceof Method) {
                        builder.method((Method) value);
                    } else {
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
                case "peerPublicKey":
                    builder.peerPublicKey((String) value);
                    break;
                case "privateKeyFile":
                    builder.privateKeyFile((String) value);
                    break;
                case "publicKey":
                    builder.publicKey((String) value);
                    break;
                case "autoCreate":
                    builder.autoCreate((Boolean) value);
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
                    } else if (value instanceof ByteBuffer) {
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
                    } else if (value instanceof ByteBuffer) {
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
                default:
                    break;
            }
        }
        return builder.build();
    }
}
