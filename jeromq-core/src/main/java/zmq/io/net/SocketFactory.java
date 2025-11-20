package zmq.io.net;

import java.io.IOException;
import java.net.SocketAddress;

import zmq.Options;

public abstract class SocketFactory<S extends SocketAddress> {
    @FunctionalInterface
    public interface ChannelFactoryWrapper<S extends SocketAddress> {
        SocketFactory<S> wrap(SocketFactory<S> factory);
        default SocketFactory<S> wrap(NetProtocol protocol) {
            return wrap(protocol.factory());
        }
    }
    public abstract ServerSocketWrapper<S> makeServerSocket(Options options) throws IOException;
    public abstract SocketWrapper<S> makeSocket(Options options) throws IOException;
    @SuppressWarnings("unchecked")
    public static final ChannelFactoryWrapper<? extends SocketAddress> TRANSPARENT = f -> f;

}
