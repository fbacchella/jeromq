package org.zeromq;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumerates types of monitoring events.
 */
public enum Events {
    CONNECTED(ZMQ.EVENT_CONNECTED),
    CONNECT_DELAYED(ZMQ.EVENT_CONNECT_DELAYED),
    CONNECT_RETRIED(ZMQ.EVENT_CONNECT_RETRIED),
    LISTENING(ZMQ.EVENT_LISTENING),
    BIND_FAILED(ZMQ.EVENT_BIND_FAILED),
    ACCEPTED(ZMQ.EVENT_ACCEPTED),
    ACCEPT_FAILED(ZMQ.EVENT_ACCEPT_FAILED),
    CLOSED(ZMQ.EVENT_CLOSED),
    CLOSE_FAILED(ZMQ.EVENT_CLOSE_FAILED),
    DISCONNECTED(ZMQ.EVENT_DISCONNECTED),
    MONITOR_STOPPED(ZMQ.EVENT_MONITOR_STOPPED),
    HANDSHAKE_FAILED_NO_DETAIL(ZMQ.HANDSHAKE_FAILED_NO_DETAIL),
    HANDSHAKE_SUCCEEDED(ZMQ.HANDSHAKE_SUCCEEDED),
    HANDSHAKE_FAILED_PROTOCOL(ZMQ.HANDSHAKE_FAILED_PROTOCOL),
    HANDSHAKE_FAILED_AUTH(ZMQ.HANDSHAKE_FAILED_AUTH),
    HANDSHAKE_PROTOCOL(ZMQ.EVENT_HANDSHAKE_PROTOCOL),
    EXCEPTION(ZMQ.ZMQ_EVENT_EXCEPTION),
    ALL(ZMQ.EVENT_ALL);

    private static final Map<Integer, Events> MAP;

    static {
        Map<Integer, Events> workMap = new HashMap<>(Events.values().length * 2);
        for (Events e : Events.values()) {
            workMap.put(e.code, e);
        }
        MAP = Map.copyOf(workMap);
    }

    private final int code;

    Events(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * Find the {@link Events} associated with the numerical event code.
     *
     * @param event the numerical event code
     * @return the found {@link Events}
     */
    public static Events findByCode(int event) {
        return MAP.getOrDefault(event, ALL);
    }
}
