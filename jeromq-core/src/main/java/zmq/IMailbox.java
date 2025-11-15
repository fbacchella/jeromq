package zmq;

import java.io.Closeable;

public interface IMailbox extends Closeable
{
    void send(Command cmd);

    Command recv(long timeout);
}
