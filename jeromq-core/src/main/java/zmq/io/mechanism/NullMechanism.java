package zmq.io.mechanism;

import zmq.Msg;
import zmq.Options;
import zmq.ZError;
import zmq.ZMQ;
import zmq.io.SessionBase;
import zmq.io.net.Address;

import static zmq.io.Metadata.IDENTITY;
import static zmq.io.Metadata.SOCKET_TYPE;

class NullMechanism extends Mechanism
{
    private static final String OK    = "200";
    private static final String READY = "READY";
    private static final String ERROR = "ERROR";

    private boolean readyCommandSent;
    private boolean errorCommandSent;

    private boolean readyCommandReceived;
    private boolean errorCommandReceived;

    private boolean zapConnected;
    private boolean zapRequestSent;
    private boolean zapReplyReceived;

    NullMechanism(SessionBase session, Address<?> peerAddress, Options options)
    {
        super(session, peerAddress, options);

        //  NULL mechanism only uses ZAP if there's a domain defined
        //  This prevents ZAP requests on naive sockets
        if (options.zapDomain != null && !options.zapDomain.isEmpty() && session.zapConnect() == 0) {
            zapConnected = true;
        }
    }

    @Override
    public int nextHandshakeCommand(Msg msg)
    {
        int rc;
        if (readyCommandSent || errorCommandSent) {
            rc = ZError.EAGAIN;
        }
        else if (zapConnected && !zapReplyReceived) {
            if (zapRequestSent) {
                rc = ZError.EAGAIN;
            }
            else {
                sendZapRequest(Mechanisms.NULL, false);
                zapRequestSent = true;

                rc = receiveAndProcessZapReply();
                if (rc == 0) {
                    zapReplyReceived = true;
                }
            }
        }
        else if (zapReplyReceived && !OK.equals(statusCode)) {
            msg.putShortString(ERROR);
            msg.putShortString(statusCode);

            errorCommandSent = true;
            rc = 0;
        }
        else {
            //  Add mechanism string
            msg.putShortString(READY);

            //  Add socket type property
            String socketType = socketType();
            addProperty(msg, SOCKET_TYPE, socketType);

            //  Add identity property
            if (options.type == ZMQ.ZMQ_REQ || options.type == ZMQ.ZMQ_DEALER || options.type == ZMQ.ZMQ_ROUTER) {
                addProperty(msg, IDENTITY, options.identity);
            }
            readyCommandSent = true;

            rc = 0;
        }
        return rc;
    }

    @Override
    public int processHandshakeCommand(Msg msg)
    {
        if (readyCommandReceived || errorCommandReceived) {
            session.getSocket().eventHandshakeFailedProtocol(session.getEndpoint(), ZMQ.ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND);
            return ZError.EPROTO;
        }
        int dataSize = msg.size();

        int rc;
        if (dataSize >= 6 && compare(msg, READY, true)) {
            rc = processReadyCommand(msg);
        }
        else if (dataSize >= 6 && compare(msg, ERROR, true)) {
            rc = processErrorCommand(msg);
        }
        else {
            session.getSocket().eventHandshakeFailedProtocol(session.getEndpoint(), ZMQ.ZMQ_PROTOCOL_ERROR_ZMTP_UNEXPECTED_COMMAND);
            rc = ZError.EPROTO;
        }
        return rc;
    }

    private int processReadyCommand(Msg msg)
    {
        readyCommandReceived = true;
        return parseMetadata(msg, 6, false);
    }

    private int processErrorCommand(Msg msg)
    {
        errorCommandReceived = true;
        return parseErrorMessage(msg);
    }

    @Override
    public int zapMsgAvailable()
    {
        if (zapReplyReceived) {
            return ZError.EFSM;
        }
        int rc = receiveAndProcessZapReply();
        if (rc == 0) {
            zapReplyReceived = true;
        }

        return rc;
    }

    @Override
    public Status status()
    {
        boolean commandSent = readyCommandSent || errorCommandSent;
        boolean commandReceived = readyCommandReceived || errorCommandReceived;

        if (readyCommandSent && readyCommandReceived) {
            return Status.READY;
        }
        else if (commandSent && commandReceived) {
            return Status.ERROR;
        }
        else {
            return Status.HANDSHAKING;
        }
    }

    @Override
    public String name()
    {
        return Mechanisms.NULL.name();
    }

    static class NullMechanismSettings implements MechanismSettings<NullMechanismSettings> {
        @Override
        public Mechanisms getMechanism()
        {
            return Mechanisms.NULL;
        }

        public boolean isServer() {
            return false;
        }

        @Override
        public NullMechanismSettings resolve()
        {
            return this;
        }

         @Override
        public Mechanism create(SessionBase session, Address<?> peerAddress, Options options)
        {
            return new NullMechanism(session, peerAddress, options);
        }

        @Override
        public boolean canZap() {
            return false;
        }

        @Override
        public String name() {
            return "NULL";
        }
    }
}
