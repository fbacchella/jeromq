package zmq.socket.pubsub;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import zmq.Msg;
import zmq.pipe.Pipe;

public class Dist
{
    private static class State
    {
        boolean matching;
        boolean active;
        boolean eligible;
    }

    //  List of outbound pipes.
    private final Map<Pipe, State> pipes;

    //  Number of all the pipes to send the next message to.
    // private int matching;

    //  Number of active pipes. All the active pipes are located at the
    //  beginning of the pipes array. These are the pipes the messages
    //  can be sent to at the moment.
    //private int active;

    //  Number of pipes eligible for sending messages to. This includes all
    //  the active pipes plus all the pipes that we can in theory send
    //  messages to (the HWM is not yet reached), but sending a message
    //  to them would result in partial message being delivered, ie. message
    //  with initial parts missing.
    //private int eligible;

    //  True if last we are in the middle of a multipart message.
    private boolean more;

    public Dist()
    {
        more = false;
        pipes = new HashMap<>();
    }

    //  Adds the pipe to the distributor object.
    public void attach(Pipe pipe)
    {
        State newState = new State();
        State oldState = pipes.put(pipe, newState);
        assert oldState == null;
        newState.eligible = true;
        //  If we are in the middle of sending a message, we'll add new pipe
        //  into the list of eligible pipes. Otherwise, we add it to the list
        //  of active pipes.
        newState.active = ! more;
        newState.matching = false;
    }

    //  Mark the pipe as matching. Subsequent call to sendToMatching
    //  will send message also to this pipe.
    public void match(Pipe pipe)
    {
        pipes.get(pipe).matching = pipes.get(pipe).eligible;
    }

    //  Mark all pipes as non-matching.
    public void unmatch()
    {
        pipes.values().forEach(s -> s.matching = false);

    }

    //  Removes the pipe from the distributor object.
    public void terminated(Pipe pipe)
    {
        pipes.remove(pipe);
    }

    //  Activates pipe that have previously reached high watermark.
    public void activated(Pipe pipe)
    {
        State s = pipes.get(pipe);
        s.eligible = true;
        //  If there's no message being sent at the moment, move it to
        //  the active state.
        s.active = !more;
    }

    private void sendMsg(Msg msg, Predicate<State> checkWrite)
    {
        //  Is this end of a multipart message?
        boolean msgMore = msg.hasMore();
        pipes.entrySet().stream().forEach(e -> {
            State s = e.getValue();
            Pipe p = e.getKey();
            boolean activate = true;
            if (checkWrite.test(s)) {
                if (!p.write(msg)) {
                    s.matching = false;
                    activate = false;
                    s.eligible = false;
                }
                else {
                    s.matching = true;
                    if (!msgMore) {
                        p.flush();
                    }
                }
            }
            s.active = activate;
        });
        more = msgMore;
    }

    //  Send the message to all the outbound pipes.
    public boolean sendToAll(Msg msg)
    {
        sendMsg(msg, s -> s.active);
        return true;
    }

    //  Send the message to the matching outbound pipes.
    public boolean sendToMatching(Msg msg)
    {
        sendMsg(msg, s -> s.matching && s.active);
        return true;
    }

    public boolean hasOut()
    {
        return true;
    }

    public boolean checkHwm()
    {
        return pipes.keySet().stream().map(Pipe::checkHwm).reduce(true, Boolean::logicalAnd);
    }

    int active()
    {
        return pipes.values().stream().map(s -> s.active ? 1 : 0).reduce(0, Integer::sum);
    }

    int eligible()
    {
        return pipes.values().stream().map(s -> s.eligible ? 1 : 0).reduce(0, Integer::sum);
    }

    int matching()
    {
        return pipes.values().stream().map(s -> s.matching ? 1 : 0).reduce(0, Integer::sum);
    }
}
