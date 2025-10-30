package org.zeromq;

import org.zeromq.ZMQ.Socket;

public class ZMQQueue implements Runnable
{
    private final Socket inSocket;
    private final Socket outSocket;

    /**
     * Class constructor.
     *
     * @param inSocket
     *            input socket
     * @param outSocket
     *            output socket
     */
    public ZMQQueue(Socket inSocket, Socket outSocket)
    {
        this.inSocket = inSocket;
        this.outSocket = outSocket;
    }

    @Override
    public void run()
    {
        zmq.ZMQ.proxy(inSocket.base(), outSocket.base(), null);
    }
}
