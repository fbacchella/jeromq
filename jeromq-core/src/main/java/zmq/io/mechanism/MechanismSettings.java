package zmq.io.mechanism;

import java.nio.ByteBuffer;
import java.util.Arrays;

import zmq.Options;
import zmq.ZMQ;
import zmq.io.SessionBase;
import zmq.io.net.Address;

public interface MechanismSettings<S extends MechanismSettings> {
    Mechanisms getMechanism();
    S resolve();
    Mechanism create(SessionBase session, Address<?> peerAddress, Options options);

    default boolean isMechanism(ByteBuffer greetingRecv)
    {
        byte[] dst = new byte[20];
        greetingRecv.get(dst, 0, dst.length);

        byte[] name = name().getBytes(ZMQ.CHARSET);
        byte[] comp = Arrays.copyOf(name, 20);
        return Arrays.equals(dst, comp);
    }
    default boolean canZap() {
        return true;
    }

    String name();
}
