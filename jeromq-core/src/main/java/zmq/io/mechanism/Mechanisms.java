package zmq.io.mechanism;

import zmq.io.mechanism.NullMechanism.NullMechanismSettings;

public enum Mechanisms
{
    NULL,
    EXTERNAL,
    PLAIN,
    CURVE,
    GSSAPI;

    public static final NullMechanismSettings NULLINSTANCE = new NullMechanismSettings();
}
