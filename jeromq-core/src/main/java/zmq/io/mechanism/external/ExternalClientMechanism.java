package zmq.io.mechanism.external;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.SessionBase;
import zmq.io.mechanism.Mechanism;
import zmq.io.mechanism.Mechanisms;

import static zmq.io.Metadata.SOCKET_TYPE;

public class ExternalClientMechanism extends Mechanism
{
    private enum State
    {
        SENDING_READY,
        WAITING_FOR_WELCOME,
        WAITING_FOR_READY,
        ERROR_COMMAND_RECEIVED,
        READY
    }

    private State state;

    public ExternalClientMechanism(SessionBase session, ExternalMechanismSettings settings, Options options)
    {
        super(session, null, options);
        this.state = State.SENDING_READY;
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        int rc;
        switch (state) {
        case SENDING_READY:
            msg.putShortString("READY");
            //  Add socket type property
            String socketType = socketType();
            addProperty(msg, SOCKET_TYPE, socketType);
            rc = 0;
            state = State.READY;
            break;
        default:
            rc = ZError.EAGAIN;
            break;
        }
        return rc;
    }

    @Override
    public int processHandshakeCommand(Msg msg)
    {
        int rc;

        int dataSize = msg.size();
        if (dataSize >= 8 && compare(msg, "WELCOME", true)) {
            rc = processWelcome(msg);
        }
        else if (dataSize >= 6 && compare(msg, "READY", true)) {
            rc = processReady(msg);
        }
        else if (dataSize >= 6 && compare(msg, "ERROR", true)) {
            rc = processError(msg);
        }
        else {
            //  Temporary support for security debugging
            System.out.println("EXTERNAL Client I: invalid handshake command");
            rc = ZError.EPROTO;
        }
        return rc;
    }

    @Override
    public Status status()
    {
        if (state == State.READY) {
            return Status.READY;
        }
        else if (state == State.ERROR_COMMAND_RECEIVED) {
            return Status.ERROR;
        }
        else {
            return Status.HANDSHAKING;
        }
    }

    @Override
    public String name()
    {
        return "NULL";
    }

    @Override
    public int zapMsgAvailable()
    {
        return 0;
    }

    private int processWelcome(Msg msg)
    {
        if (state != State.WAITING_FOR_WELCOME) {
            return ZError.EPROTO;
        }
        if (msg.size() != 8) {
            return ZError.EPROTO;
        }
        state = State.SENDING_READY;
        return 0;
    }

    private int processReady(Msg msg)
    {
        if (state != State.WAITING_FOR_READY) {
            return ZError.EPROTO;
        }
        int rc = parseMetadata(msg, 6, false);
        if (rc == 0) {
            state = State.READY;
        }

        return rc;
    }

    private int processError(Msg msg)
    {
        if (state != State.WAITING_FOR_WELCOME && state != State.WAITING_FOR_READY) {
            session.getSocket().eventHandshakeFailedProtocol(session.getEndpoint(), ZMQ.ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND);
            return ZError.EPROTO;
        }
        state = State.ERROR_COMMAND_RECEIVED;
        return parseErrorMessage(msg);
    }
}
