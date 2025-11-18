package org.zeromq;

import java.util.HashMap;
import java.util.Map;

import zmq.ZError;

/**
 * Resolve code from errornumber.
 * <p>
 * Messages are taken from https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/errno.h.html
 */
public enum Errors {
    NOERROR(0, "No error"),
    ENOTSUP(ZError.ENOTSUP, "Not supported"),
    EPROTONOSUPPORT(ZError.EPROTONOSUPPORT, "Protocol not supported"),
    ENOBUFS(ZError.ENOBUFS, "No buffer space available"),
    ENETDOWN(ZError.ENETDOWN, "Network is down"),
    EADDRINUSE(ZError.EADDRINUSE, "Address already in use"),
    EADDRNOTAVAIL(ZError.EADDRNOTAVAIL, "Address not available"),
    ECONNREFUSED(ZError.ECONNREFUSED, "Connection refused"),
    EINPROGRESS(ZError.EINPROGRESS, "Operation in progress"),
    EHOSTUNREACH(ZError.EHOSTUNREACH, "Host unreachable"),
    EMTHREAD(ZError.EMTHREAD, "No thread available"),
    EFSM(ZError.EFSM, "Operation cannot be accomplished in current state"),
    ENOCOMPATPROTO(ZError.ENOCOMPATPROTO, "The protocol is not compatible with the socket type"),
    ETERM(ZError.ETERM, "Context was terminated"),
    ENOTSOCK(ZError.ENOTSOCK, "Not a socket"),
    EAGAIN(ZError.EAGAIN, "Resource unavailable, try again"),
    ENOENT(ZError.ENOENT, "No such file or directory"),
    EINTR(ZError.EINTR, "Interrupted function"),
    EACCESS(ZError.EACCESS, "Permission denied"),
    EFAULT(ZError.EFAULT, "Bad address"),
    EINVAL(ZError.EINVAL, "Invalid argument"),
    EISCONN(ZError.EISCONN, "Socket is connected"),
    ENOTCONN(ZError.ENOTCONN, "The socket is not connected"),
    EMSGSIZE(ZError.EMSGSIZE, "Message too large"),
    EAFNOSUPPORT(ZError.EAFNOSUPPORT, "Address family not supported"),
    ENETUNREACH(ZError.ENETUNREACH, "Network unreachable"),
    ECONNABORTED(ZError.ECONNABORTED, "Connection aborted"),
    ECONNRESET(ZError.ECONNRESET, "Connection reset"),
    ETIMEDOUT(ZError.ETIMEDOUT, "Connection timed out"),
    ENETRESET(ZError.ENETRESET, "Connection aborted by network"),
    EIOEXC(ZError.EIOEXC),
    ESOCKET(ZError.ESOCKET),
    EMFILE(ZError.EMFILE, "File descriptor value too large"),
    EPROTO(ZError.EPROTO, "Protocol error");

    private static final Map<Integer, Errors> MAP;

    static {
        Map<Integer, Errors> workMap = new HashMap<>(Errors.values().length * 2);
        for (Errors e : Errors.values()) {
            workMap.put(e.code, e);
        }
        MAP = Map.copyOf(workMap);
    }

    private final int code;
    private final String message;

    Errors(int code)
    {
        this.code = code;
        this.message = "errno " + code;
    }

    Errors(int code, String message)
    {
        this.code = code;
        this.message = message;
    }

    public int getCode()
    {
        return code;
    }

    public String getMessage()
    {
        return message;
    }

    public static Errors findByCode(int code)
    {
        if (code <= 0) {
            return NOERROR;
        } else if (MAP.containsKey(code)) {
            return MAP.get(code);
        } else {
            throw new IllegalArgumentException("Unknown " + Errors.class.getName() + " enum code: " + code);
        }
    }
}
