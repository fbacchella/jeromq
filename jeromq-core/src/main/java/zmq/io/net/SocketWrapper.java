package zmq.io.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

import zmq.io.Metadata;

public interface SocketWrapper<S extends SocketAddress> extends ByteChannel
{
    default void plug() throws IOException
    {

    }
    int write(ByteBuffer inBuffer) throws IOException;
    int read(ByteBuffer outBuffer) throws IOException;
    void close() throws IOException;
    default void resolveMetadata(Metadata metadata)
    {

    }

    boolean connect(S sa) throws IOException;

    boolean finishConnect() throws IOException;

    SocketChannel getNativeSocket();

    boolean isOpen();

    void unblocking() throws IOException;

    Address<S> getPeerSocketAddress();

    Address<S> getLocalSocketAddress();

    SelectableChannel getSelectableChannel();

    void tune();

    void configureBlocking(boolean b) throws IOException;

    boolean isBlocking();
}
