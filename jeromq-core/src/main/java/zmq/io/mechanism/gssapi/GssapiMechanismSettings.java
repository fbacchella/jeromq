package zmq.io.mechanism.gssapi;

import zmq.Options;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.MechanismSettings;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.Address;

public class GssapiMechanismSettings implements MechanismSettings<GssapiMechanismSettings>
{
    @Override
    public Mechanisms getMechanism()
    {
        return Mechanisms.GSSAPI;
    }

    @Override
    public GssapiMechanismSettings resolve()
    {
        return this;
    }

    public Object gssPrincipal()
    {
        return null;
    }

    public Object gssServicePrincipal()
    {
        return null;
    }

    public Object gssPlaintext()
    {
        return null;
    }

    public boolean isServer()
    {
        return false;
    }

    @Override
    public Mechanism create(SessionBase session, Address<?> peerAddress, Options options)
    {
        throw new UnsupportedOperationException("GSSAPI mechanism is not yet implemented");
    }

    @Override
    public String name()
    {
        return "GSSAPI";
    }
}
