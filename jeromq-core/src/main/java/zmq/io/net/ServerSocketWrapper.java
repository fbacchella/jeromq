package zmq.io.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;

import zmq.io.net.Address.IZAddress;

public interface ServerSocketWrapper<S extends SocketAddress> extends AutoCloseable {
    SocketWrapper<S> accept(IZAddress<S> address) throws IOException;

    void close() throws IOException;

    SelectableChannel getSelectableChannel();

    void configureBlocking(boolean b) throws IOException;

    void bind(S socketAddress) throws IOException;

    S getAddress() throws IOException;
}
