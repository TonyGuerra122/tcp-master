package com.tonyguerra.net.tcpmaster.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Path;

public interface TcpSession {
    Socket socket();

    InputStream in();

    OutputStream out();

    void beginBinary(long bytes);

    boolean isBinaryMode();

    // NEW: pending file target for binary upload
    void setPendingBinaryTarget(Path target);

    Path getPendingBinaryTarget();
}
