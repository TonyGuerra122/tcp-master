package com.tonyguerra.net.tcpmaster.errors;

import java.net.SocketException;

public final class TcpException extends SocketException {
    public TcpException(Throwable ex) {
        super(ex.getMessage());
    }

    public TcpException(String message) {
        super(message);
    }
}
