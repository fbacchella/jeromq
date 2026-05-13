package zmq.io.mechanism.gssapi;

import zmq.Msg;
import zmq.Options;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.Mechanisms;

public class GssapiClientMechanism extends Mechanism
{
    public GssapiClientMechanism(SessionBase session, Options options)
    {
        super(session, null, options);
        throw new UnsupportedOperationException("GSSAPI mechanism is not yet implemented");
    }

    @Override
    public Status status()
    {
        return null;
    }

    @Override
    public String name()
    {
        return Mechanisms.GSSAPI.name();
    }

    @Override
    public int zapMsgAvailable()
    {
        return 0;
    }

    @Override
    public int processHandshakeCommand(Msg msg)
    {
        return 0;
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        return 0;
    }
}
