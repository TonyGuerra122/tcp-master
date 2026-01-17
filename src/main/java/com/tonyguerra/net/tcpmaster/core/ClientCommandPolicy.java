package com.tonyguerra.net.tcpmaster.core;

public enum ClientCommandPolicy {
    /**
     * If a client command exists, execute it locally and do NOT send to the server.
     * If it doesn't exist, send to server normally.
     */
    LOCAL_ONLY,

    /**
     * If a client command exists, execute it locally AND also send to the server.
     */
    LOCAL_AND_REMOTE,

    /**
     * Never execute client handlers. Always send to the server.
     */
    REMOTE_ONLY;
}
