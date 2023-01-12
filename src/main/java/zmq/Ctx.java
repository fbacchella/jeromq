package zmq;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.zeromq.ZMQException;

import zmq.io.IOThread;
import zmq.pipe.Pipe;
import zmq.socket.Sockets;
import zmq.util.Errno;

//Context object encapsulates all the global state associated with
//  the library.

public class Ctx
{
    private static final int WAIT_FOREVER = -1;

    //  Information associated with inproc endpoint. Note that endpoint options
    //  are registered as well so that the peer can access them without a need
    //  for synchronization, handshaking or similar.

    public static class Endpoint
    {
        public final SocketBase socket;
        public final Options    options;

        public Endpoint(SocketBase socket, Options options)
        {
            this.socket = socket;
            this.options = options;
        }

    }

    private static class PendingConnection
    {
        private final Endpoint endpoint;
        private final Pipe     connectPipe;
        private final Pipe     bindPipe;

        public PendingConnection(Endpoint endpoint, Pipe connectPipe, Pipe bindPipe)
        {
            super();
            this.endpoint = endpoint;
            this.connectPipe = connectPipe;
            this.bindPipe = bindPipe;
        }
    }

    private enum Side
    {
        CONNECT,
        BIND
    }

    //  Used to check whether the object is a context.
    private boolean active;

    //  Sockets belonging to this context. We need the list so that
    //  we can notify the sockets when zmq_term() is called. The sockets
    //  will return ETERM then.
    private final Set<SocketBase> sockets;

    //  List of unused thread slots.
    private final Deque<Integer> emptySlots;

    //  If true, init has been called but no socket has been created
    //  yet. Launching of I/O threads is delayed.
    private final AtomicBoolean starting = new AtomicBoolean(true);

    //  If true, zmq_term was already called.
    private boolean terminating;

    //  A read-write lock to ensure that initSlots locks all access
    private final ReadWriteLock slotSync;

    // A list of poll selectors opened under this context. When the context is
    // destroyed, each of the selectors is closed to ensure resource
    // deallocation.
    private final Set<Selector> selectors;

    //  The reaper thread.
    private Reaper reaper;

    //  I/O threads.
    private final List<IOThread> ioThreads;

    // Using AtomicReference to ensure consistent view between threads
    private AtomicReference<IMailbox>[] slots;

    //  Mailbox for zmq_term thread.
    private final Mailbox termMailbox;

    //  List of inproc endpoints within this context.
    private final Map<String, Endpoint> endpoints;

    //  Maximum socket ID.
    private static final AtomicInteger maxSocketId = new AtomicInteger(0);

    //  Maximum number of sockets that can be opened at the same time.
    private int maxSockets;

    //  Number of I/O threads to launch.
    private int ioThreadCount;

    //  Does context wait (possibly forever) on termination?
    private boolean blocky;

    //  Synchronization of access to context options.
    private final Lock optSync;

    static final int         TERM_TID   = 0;
    private static final int REAPER_TID = 1;

    private final Map<String, Set<PendingConnection>> pendingConnections;

    private boolean ipv6;

    private final Errno errno = new Errno();

    // Exception handlers to receive notifications of critical exceptions in zmq.poll.Poller and handle uncaught exceptions
    private UncaughtExceptionHandler exhandler = Thread.getDefaultUncaughtExceptionHandler();

    // Exception handlers to receive notifications of exception in zmq.poll.Poller, and can be used for logging
    private UncaughtExceptionHandler exnotification = (t, e) -> e.printStackTrace();

    /**
     * A class that holds the informations needed to forward channel in monitor sockets.
     * Of course, it only works with inproc sockets.
     * <p>
     * It uses WeakReference to avoid holding references to channel if the monitor event is
     * lost.
     * <p>
     * A class is used as a lock in lazy allocation of the needed objects.
     */
    private static class ChannelForwardHolder
    {
        private final AtomicInteger handleSource = new AtomicInteger(0);
        private final Map<Integer, WeakReference<SelectableChannel>> map = new ConcurrentHashMap<>();
        // The WeakReference is empty when the reference is empty, so keep a reverse empty to clean the direct map.
        private final Map<WeakReference<SelectableChannel>, Integer> reversemap = new ConcurrentHashMap<>();
        private final ReferenceQueue<SelectableChannel> queue = new ReferenceQueue<>();
    }

    private ChannelForwardHolder forwardHolder = null;

    public Ctx()
    {
        active = true;
        terminating = false;
        reaper = null;
        slots = null;
        maxSockets = ZMQ.ZMQ_MAX_SOCKETS_DFLT;
        ioThreadCount = ZMQ.ZMQ_IO_THREADS_DFLT;

        ipv6 = false;
        blocky = true;
        slotSync = new ReentrantReadWriteLock();
        optSync = new ReentrantLock();

        emptySlots = new ConcurrentLinkedDeque<>();
        ioThreads = new ArrayList<>();
        sockets = ConcurrentHashMap.newKeySet();
        endpoints = new ConcurrentHashMap<>();
        selectors = ConcurrentHashMap.newKeySet();
        pendingConnections = new ConcurrentHashMap<>();
        termMailbox = new Mailbox(this, "terminater", -1);
    }

    private void destroy() throws IOException
    {
        assert (sockets.isEmpty());

        for (IOThread it : ioThreads) {
            it.stop();
        }
        for (IOThread it : ioThreads) {
            it.close();
        }
        ioThreads.clear();

        for (Iterator<Selector> it = selectors.iterator(); it.hasNext(); ) {
            Selector selector = it.next();
            if (selector != null) {
                selector.close();
                it.remove();
            }
        }

        //  Deallocate the reaper thread object.
        if (reaper != null) {
            reaper.close();
        }
        //  Deallocate the array of mailboxes. No special work is
        //  needed as mailboxes themselves were deallocated with their
        //  corresponding io_thread/socket objects.
        termMailbox.close();

        active = false;
    }

    /**
     * @return false if {@link #terminate()}terminate() has been called.
     */
    public boolean isActive()
    {
        return active;
    }

    /**
     * @return false if {@link #terminate()}terminate() has been called.
     * @deprecated use {@link #isActive()} instead
     */
    @Deprecated
    public boolean checkTag()
    {
        return active;
    }

    //  This function is called when user invokes zmq_term. If there are
    //  no more sockets open it'll cause all the infrastructure to be shut
    //  down. If there are open sockets still, the deallocation happens
    //  after the last one is closed.

    public void terminate()
    {
        slotSync.writeLock().lock();
        try {
            // Connect up any pending inproc connections, otherwise we will hang
            for (String pending : pendingConnections.keySet()) {
                SocketBase s = createSocket(ZMQ.ZMQ_PAIR);
                // create_socket might fail eg: out of memory/sockets limit reached
                assert (s != null);
                s.bind(pending);
                s.close();
            }

            if (!starting.get()) {
                //  Check whether termination was already underway, but interrupted and now
                //  restarted.
                boolean restarted = terminating;
                terminating = true;

                //  First attempt to terminate the context.
                if (!restarted) {
                    //  First send stop command to sockets so that any blocking calls
                    //  can be interrupted. If there are no sockets we can ask reaper
                    //  thread to stop.
                    for (SocketBase socket : sockets) {
                        socket.stop();
                    }
                    if (sockets.isEmpty()) {
                        reaper.stop();
                    }
                }
            }
        }
        finally {
            slotSync.writeLock().unlock();
        }

        if (!starting.get()) {
            //  Wait till reaper thread closes all the sockets.
            Command cmd = termMailbox.recv(WAIT_FOREVER);
            if (cmd == null) {
                throw new ZMQException(errno.get());
            }
            assert (cmd.type == Command.Type.DONE) : cmd;

            assert sockets.isEmpty();
        }

        //  Deallocate the resources.
        try {
            destroy();
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
    }

    final void shutdown()
    {
        slotSync.writeLock().lock();
        try {
            if (!starting.get() && !terminating) {
                terminating = true;
                //  Send stop command to sockets so that any blocking calls
                //  can be interrupted. If there are no sockets we can ask reaper
                //  thread to stop.
                for (SocketBase socket : sockets) {
                    socket.stop();
                }
                if (sockets.isEmpty()) {
                    reaper.stop();
                }

            }
        }
        finally {
            slotSync.writeLock().unlock();
        }
    }

    /**
     * Set the handler invoked when a {@link zmq.poll.Poller} abruptly terminates due to an uncaught exception.<p>
     * It default to the value of {@link Thread#getDefaultUncaughtExceptionHandler()}
     * @param handler The object to use as this thread's uncaught exception handler. If null then this thread has no explicit handler.
     */
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler handler)
    {
        if  (!starting.get()) {
            throw new IllegalStateException("Already started");
        }
        exhandler = handler;
    }

    /**
     * @return The handler invoked when a {@link zmq.poll.Poller} abruptly terminates due to an uncaught exception.
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler()
    {
        return exhandler;
    }

    /**
     * In {@link zmq.poll.Poller#run()}, some non-fatal exceptions can be thrown. This handler will be notified, so they can
     * be logged.<p>
     * Default to {@link Throwable#printStackTrace()}
     * @param handler The object to use as this thread's handler for recoverable exceptions notifications.
     */
    public void setNotificationExceptionHandler(UncaughtExceptionHandler handler)
    {
        if  (!starting.get()) {
            throw new IllegalStateException("Already started");
        }
        exnotification = handler;
    }

    /**
     * @return The handler invoked when a non-fatal exceptions is thrown in zmq.poll.Poller#run()
     */
    public UncaughtExceptionHandler getNotificationExceptionHandler()
    {
        return exnotification;
    }

    public boolean set(int option, int optval)
    {
        if (option == ZMQ.ZMQ_MAX_SOCKETS && optval >= 1) {
            optSync.lock();
            try {
                maxSockets = optval;
            }
            finally {
                optSync.unlock();
            }
        }
        else if (option == ZMQ.ZMQ_IO_THREADS && optval >= 0) {
            optSync.lock();
            try {
                ioThreadCount = optval;
            }
            finally {
                optSync.unlock();
            }
        }
        else if (option == ZMQ.ZMQ_BLOCKY && optval >= 0) {
            optSync.lock();
            try {
                blocky = (optval != 0);
            }
            finally {
                optSync.unlock();
            }
        }
        else if (option == ZMQ.ZMQ_IPV6 && optval >= 0) {
            optSync.lock();
            try {
                ipv6 = (optval != 0);
            }
            finally {
                optSync.unlock();
            }
        }
        else {
            return false;
        }
        return true;
    }

    public int get(int option)
    {
        int rc;
        if (option == ZMQ.ZMQ_MAX_SOCKETS) {
            rc = maxSockets;
        }
        else if (option == ZMQ.ZMQ_IO_THREADS) {
            rc = ioThreadCount;
        }
        else if (option == ZMQ.ZMQ_BLOCKY) {
            rc = blocky ? 1 : 0;
        }
        else if (option == ZMQ.ZMQ_IPV6) {
            rc = ipv6 ? 1 : 0;
        }
        else {
            throw new IllegalArgumentException("option = " + option);
        }
        return rc;
    }

    public SocketBase createSocket(int type)
    {
        // This section use the write lock as the ReentrantReadWriteLock can't be upgraded from read to write
        slotSync.writeLock().lock();
        try {
            if (starting.compareAndSet(true, false)) {
                initSlots();
            }
        }
        finally {
            slotSync.writeLock().unlock();
        }
        slotSync.readLock().lock();
        try {
            //  Once zmq_term() was called, we can't create new sockets.
            if (terminating) {
                throw new ZError.CtxTerminatedException();
            }

            //  Choose a slot for the socket.
            Integer slot = emptySlots.pollLast();
            //  If maxSockets limit was reached, return error.
            if (slot == null) {
                throw new ZMQException(ZError.EMFILE);
            }

            //  Generate new unique socket ID.
            int sid = maxSocketId.incrementAndGet();

            //  Create the socket and register its mailbox.
            SocketBase s = Sockets.create(type, this, slot, sid);
            if (s == null) {
                emptySlots.addLast(slot);
                return null;
            }
            else {
                sockets.add(s);
                slots[slot].set(s.getMailbox());
                return s;
            }
        }
        finally {
            slotSync.readLock().unlock();
        }

    }

    private void initSlots()
    {
        int slotCount;
        slotSync.writeLock().lock();
        try {
            //  Initialize the array of mailboxes. Additional two slots are for
            //  zmq_term thread and reaper thread.
            int ios;
            optSync.lock();
            try {
                ios = ioThreadCount;
                slotCount = maxSockets + ioThreadCount + 2;
            }
            finally {
                optSync.unlock();
            }
            slots = new AtomicReference[slotCount];

            //  Initialize the infrastructure for zmq_term thread.
            slots[TERM_TID] = new AtomicReference<>(termMailbox);

            //  Create the reaper thread.
            reaper = new Reaper(this, REAPER_TID);
            slots[REAPER_TID] = new AtomicReference<>(reaper.getMailbox());
            reaper.start();

            //  Create I/O thread objects and launch them.
            for (int i = 2; i != ios + 2; i++) {
                IOThread ioThread = new IOThread(this, i);
                ioThreads.add(ioThread);
                slots[i] = new AtomicReference<>(ioThread.getMailbox());
                ioThread.start();
            }

            //  In the unused part of the slot array, create a list of empty slots.
            for (int i = slotCount - 1; i >= ios + 2; i--) {
                emptySlots.add(i);
                slots[i] = new AtomicReference<>(null);
            }
        }
        finally {
            slotSync.writeLock().unlock();
        }
    }

    void destroySocket(SocketBase socket)
    {
        slotSync.readLock().lock();

        //  Free the associated thread slot.
        try {
            int tid = socket.getTid();
            slots[tid].set(null);
            emptySlots.add(tid);

            //  Remove the socket from the list of sockets.
            sockets.remove(socket);

            //  If zmq_term() was already called and there are no more socket
            //  we can ask reaper thread to terminate.
            if (terminating && sockets.isEmpty()) {
                reaper.stop();
            }
        }
        finally {
            slotSync.readLock().unlock();
        }
    }

    // Creates a Selector that will be closed when the context is destroyed.
    public Selector createSelector()
    {
        try {
            Selector selector = Selector.open();
            assert (selector != null);
            selectors.add(selector);
            return selector;
        }
        catch (IOException e) {
            throw new ZError.IOException(e);
        }
    }

    public boolean closeSelector(Selector selector)
    {
        boolean rc = selectors.remove(selector);
        if (rc) {
            try {
                selector.close();
            }
            catch (IOException e) {
                throw new ZError.IOException(e);
            }
        }
        return rc;
    }

    //  Returns reaper thread object.
    ZObject getReaper()
    {
        return reaper;
    }

    //  Send command to the destination thread.
    void sendCommand(int tid, final Command command)
    {
        slots[tid].get().send(command);
    }

    //  Returns the I/O thread that is the least busy at the moment.
    //  Affinity specifies which I/O threads are eligible (0 = all).
    //  Returns NULL if no I/O thread is available.
    IOThread chooseIoThread(long affinity)
    {
        if (ioThreads.isEmpty()) {
            return null;
        }

        //  Find the I/O thread with minimum load.
        int minLoad = -1;
        IOThread selectedIoThread = null;

        for (int i = 0; i != ioThreads.size(); i++) {
            if (affinity == 0 || (affinity & (1L << i)) > 0) {
                int load = ioThreads.get(i).getLoad();
                if (selectedIoThread == null || load < minLoad) {
                    minLoad = load;
                    selectedIoThread = ioThreads.get(i);
                }
            }
        }
        return selectedIoThread;
    }

    //  Management of inproc endpoints.
    boolean registerEndpoint(String addr, Endpoint endpoint)
    {
         Endpoint inserted = endpoints.put(addr, endpoint);

        return inserted == null;
    }

    boolean unregisterEndpoint(String addr, SocketBase socket)
    {
        AtomicReference<Endpoint> endpoint = new AtomicReference<>();
        endpoints.compute(addr, (k, v) -> {
            if (v != null && socket == v.socket) {
                endpoint.set(v);
                return null;
            }
            else {
                endpoint.set(null);
                return v;
            }
        });

        return endpoint.get() != null;
    }

    void unregisterEndpoints(SocketBase socket)
    {
        for (Iterator<Entry<String, Endpoint>> it = endpoints.entrySet().iterator(); it.hasNext(); ) {
            Entry<String, Endpoint> e = it.next();
            if (e.getValue().socket == socket) {
                it.remove();
            }
        }
     }

    Endpoint findEndpoint(String addr)
    {
        AtomicReference<Endpoint> endpoint = new AtomicReference<>();
        endpoints.compute(addr, (k, v) -> {
            if (v == null) {
                endpoint.set(new Endpoint(null, new Options()));
                return null;
            }
            else {
                //  Increment the command sequence number of the peer so that it won't
                //  get deallocated until "bind" command is issued by the caller.
                //  The subsequent 'bind' has to be called with inc_seqnum parameter
                //  set to false, so that the seqnum isn't incremented twice.
                v.socket.incSeqnum();
                endpoint.set(v);
                return v;
            }
        });
        return endpoint.get();
    }

    void pendConnection(String addr, Endpoint endpoint, Pipe[] pipes)
    {
        PendingConnection pendingConnection = new PendingConnection(endpoint, pipes[0], pipes[1]);

        endpoints.compute(addr, (k, v) -> {
            if (v == null) {
                // Still no bind.
                endpoint.socket.incSeqnum();
                pendingConnections.computeIfAbsent(addr, p -> ConcurrentHashMap.newKeySet()).add(pendingConnection);
            }
            else {
                // Bind has happened in the meantime, connect directly
                connectInprocSockets(v.socket, v.options, pendingConnection, Side.CONNECT);
            }
            return v;
        });
    }

    void connectPending(String addr, SocketBase bindSocket)
    {
        Endpoint endpoint = endpoints.get(addr);
        Set<PendingConnection> pendings = pendingConnections.remove(addr);
        if (pendings != null) {
            for (PendingConnection pending : pendings) {
                connectInprocSockets(bindSocket, endpoint.options, pending, Side.BIND);
            }
        }
    }

    private void connectInprocSockets(SocketBase bindSocket, Options bindOptions, PendingConnection pendingConnection,
                                      Side side)
    {
        bindSocket.incSeqnum();

        pendingConnection.bindPipe.setTid(bindSocket.getTid());
        if (!bindOptions.recvIdentity) {
            Msg msg = pendingConnection.bindPipe.read();
            assert (msg != null);
        }

        int sndhwm = 0;
        if (pendingConnection.endpoint.options.sendHwm != 0 && bindOptions.recvHwm != 0) {
            sndhwm = pendingConnection.endpoint.options.sendHwm + bindOptions.recvHwm;
        }
        int rcvhwm = 0;
        if (pendingConnection.endpoint.options.recvHwm != 0 && bindOptions.sendHwm != 0) {
            rcvhwm = pendingConnection.endpoint.options.recvHwm + bindOptions.sendHwm;
        }
        boolean conflate = pendingConnection.endpoint.options.conflate
                && (pendingConnection.endpoint.options.type == ZMQ.ZMQ_DEALER
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_PULL
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_PUSH
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_PUB
                        || pendingConnection.endpoint.options.type == ZMQ.ZMQ_SUB);
        int[] hwms = { conflate ? -1 : sndhwm, conflate ? -1 : rcvhwm };
        pendingConnection.connectPipe.setHwms(hwms[1], hwms[0]);
        pendingConnection.bindPipe.setHwms(hwms[0], hwms[1]);

        if (bindOptions.canReceiveDisconnectMsg && bindOptions.disconnectMsg != null) {
            pendingConnection.connectPipe.setDisconnectMsg(bindOptions.disconnectMsg);
        }

        if (side == Side.BIND) {
            Command cmd = new Command(null, Command.Type.BIND, pendingConnection.bindPipe);
            bindSocket.processCommand(cmd);
            bindSocket.sendInprocConnected(pendingConnection.endpoint.socket);
        }
        else {
            pendingConnection.connectPipe.sendBind(bindSocket, pendingConnection.bindPipe, false);
        }

        // When a ctx is terminated all pending inproc connection will be
        // connected, but the socket will already be closed and the pipe will be
        // in waiting_for_delimiter state, which means no more writes can be done
        // and the identity write fails and causes an assert. Check if the socket
        // is open before sending.
        if (pendingConnection.endpoint.options.recvIdentity && pendingConnection.endpoint.socket.isActive()) {
            Msg id = new Msg(bindOptions.identitySize);
            id.put(bindOptions.identity, 0, bindOptions.identitySize);
            id.setFlags(Msg.IDENTITY);
            boolean written = pendingConnection.bindPipe.write(id);
            assert (written);
            pendingConnection.bindPipe.flush();
        }

        //  If set, send the hello msg of the peer to the local socket.
        if (bindOptions.canSendHelloMsg && bindOptions.helloMsg != null) {
            boolean written = pendingConnection.bindPipe.write(bindOptions.helloMsg);
            assert (written);
            pendingConnection.bindPipe.flush();
        }
    }

    public Errno errno()
    {
        return errno;
    }

    /**
     * Forward a channel in a monitor socket.
     * @param channel a channel to forward
     * @return the handle of the channel to be forwarded, used to retrieve it in {@link #getForwardedChannel(Integer)}
     */
    int forwardChannel(SelectableChannel channel)
    {
        synchronized (ChannelForwardHolder.class) {
            if (forwardHolder == null) {
                forwardHolder = new ChannelForwardHolder();
            }
        }
        WeakReference<SelectableChannel> ref = new WeakReference<>(channel, forwardHolder.queue);
        int handle = forwardHolder.handleSource.getAndIncrement();
        forwardHolder.map.put(handle, ref);
        forwardHolder.reversemap.put(ref, handle);
        cleanForwarded();
        return handle;
    }

    /**
     * Retrieve a channel, using the handle returned by {@link #forwardChannel(SelectableChannel)}. As WeakReference are used, if the channel was discarded
     * and a GC ran, it will not be found and this method will return null.
     * @param handle a channel identifier
     * @return the channel found
     */
    SelectableChannel getForwardedChannel(Integer handle)
    {
        cleanForwarded();
        WeakReference<SelectableChannel> ref = forwardHolder.map.remove(handle);
        if (ref != null) {
            return ref.get();
        }
        else {
            return null;
        }
    }

    /**
     * Clean all empty references
     */
    private void cleanForwarded()
    {
        Reference<? extends SelectableChannel> ref;
        while ((ref = forwardHolder.queue.poll()) != null) {
            Integer handle = forwardHolder.reversemap.remove(ref);
            forwardHolder.map.remove(handle);
        }
    }
}
