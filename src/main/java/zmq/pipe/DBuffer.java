package zmq.pipe;

import zmq.Msg;

class DBuffer<T extends Msg>
{
    private T back;
    private T front;

    private boolean hasMsg;

    public synchronized T back()
    {
        return back;
    }

    public synchronized T front()
    {
        return front;
    }

    synchronized void write(T msg)
    {
        assert (msg.check());
        back = front;
        front = msg;
        hasMsg = true;
    }

    synchronized T read()
    {
        if (!hasMsg) {
            return null;
        }

        assert (front.check());
        hasMsg = false;

        return front;
    }

    synchronized boolean checkRead()
    {
        return hasMsg;
    }

    synchronized T probe()
    {
        return front;
    }
}
