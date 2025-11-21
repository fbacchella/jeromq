package zmq.io.net;

import java.net.SocketAddress;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import zmq.Options;
import zmq.Own;
import zmq.SocketBase;
import zmq.io.IEngine;
import zmq.io.IOThread;
import zmq.io.SessionBase;
import zmq.io.net.Address.IZAddress;

public interface NetworkProtocolProvider<S extends SocketAddress>
{
    boolean handleProtocol(NetProtocol protocol);
    Listener getListener(IOThread ioThread, SocketBase socket, Options options);
    IZAddress<S> zresolve(String addr, boolean ipv6);
    void startConnecting(Options options, IOThread ioThread, SessionBase session, Address<S> addr, boolean delayedStart,
            Consumer<Own> launchChild, BiConsumer<SessionBase, IEngine> sendAttach);
    default boolean isValid()
    {
        return false;
    }
    default boolean handleAdress(SocketAddress socketAddress)
    {
        return false;
    }
    default String formatSocketAddress(S socketAddress)
    {
        throw new IllegalArgumentException("Unhandled address protocol " + socketAddress);
    }
    boolean wantsIOThread();
    SocketFactory<S> channelFactory();
}
