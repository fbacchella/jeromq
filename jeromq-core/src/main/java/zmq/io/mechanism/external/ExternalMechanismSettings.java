package zmq.io.mechanism.external;

import zmq.Options;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.MechanismSettings;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.Address;

public class ExternalMechanismSettings implements MechanismSettings<ExternalMechanismSettings> {
    private final boolean asServer;

    public ExternalMechanismSettings(boolean asServer) {
        this.asServer = asServer;
    }

    @Override
    public Mechanisms getMechanism() {
        return Mechanisms.EXTERNAL;
    }

    @Override
    public ExternalMechanismSettings resolve() {
        return this;
    }
    @Override
    public Mechanism create(SessionBase session, Address<?> peerAddress, Options options)
    {
        if (asServer) {
            return new ExternalServerMechanism(session, peerAddress, this, options);
        }
        else {
            return new ExternalClientMechanism(session, this, options);
        }
    }

    @Override
    public String name() {
        return "NULL";
    }

}
