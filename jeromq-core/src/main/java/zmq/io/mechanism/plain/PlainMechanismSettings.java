package zmq.io.mechanism.plain;

import zmq.Options;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.MechanismSettings;
import zmq.io.mechanism.Mechanisms;
import zmq.io.net.Address;

public class PlainMechanismSettings implements MechanismSettings<PlainMechanismSettings> {

    private final boolean server;

    private final String username;
    private final  String password;

    public PlainMechanismSettings(boolean server, String username, String password) {
        assert (username.length() < 256);
        assert (password.length() < 256);
        this.server = server;
        this.username = username;
        this.password = password;
    }

    @Override
    public Mechanisms getMechanism() {
        return Mechanisms.PLAIN;
    }

    @Override
    public String name() {
        return "PLAIN";
    }

    public boolean isServer()
    {
        return server;
    }

    @Override
    public PlainMechanismSettings resolve() {
        return this;
    }

    public String username() {
        return this.username;
    }

    public String password() {
        return this.password;
    }

    @Override
    public Mechanism create(SessionBase session, Address<?> peerAddress, Options options)
    {
        if (server) {
            return new PlainServerMechanism(session, peerAddress, this, options);
        }
        else {
            return new PlainClientMechanism(session, this, options);
        }
    }
}
