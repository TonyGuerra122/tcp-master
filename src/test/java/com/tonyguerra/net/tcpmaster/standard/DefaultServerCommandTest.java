package com.tonyguerra.net.tcpmaster.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tonyguerra.net.tcpmaster.core.TcpServer;
import com.tonyguerra.net.tcpmaster.core.TcpSession;

final class DefaultServerCommandTest {
    static final class FakeSession implements TcpSession {
        long lastBegin = -1;
        Path pending;

        @Override
        public Socket socket() {
            return new Socket();
        }

        @Override
        public InputStream in() {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutputStream out() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void beginBinary(long bytes) {
            lastBegin = bytes;
        }

        @Override
        public boolean isBinaryMode() {
            return lastBegin > 0;
        }

        @Override
        public void setPendingBinaryTarget(Path target) {
            pending = target;
        }

        @Override
        public Path getPendingBinaryTarget() {
            return pending;
        }
    }

    @Test
    void binBeginShouldIgnoreInvalidInput() {
        final var server = new TcpServer(0);
        final var session = new FakeSession();

        DefaultServerCommands
                .beginBinary(new TcpServer.ServerCommandContext(server, new Socket(), "!bin.begin", session));
        assertEquals(-1L, session.lastBegin);

        DefaultServerCommands
                .beginBinary(new TcpServer.ServerCommandContext(server, new Socket(), "!bin.begin abc", session));
        assertEquals(-1L, session.lastBegin);

        DefaultServerCommands
                .beginBinary(new TcpServer.ServerCommandContext(server, new Socket(), "!bin.begin 0", session));
        assertEquals(-1L, session.lastBegin);

        DefaultServerCommands
                .beginBinary(new TcpServer.ServerCommandContext(server, new Socket(), "!bin.begin -10", session));
        assertEquals(-1L, session.lastBegin);
    }

    @Test
    void filePutShouldSetPendingTargetAndBeginBinaryAndReturnOkReady() {
        final var server = new TcpServer(0);
        final var session = new FakeSession();

        final var ctx = new TcpServer.ServerCommandContext(server, new Socket(), "!file.put docs/a.txt 55", session);

        // assumes filePut returns "OK READY"
        final String resp = DefaultServerCommands.filePut(ctx);
        assertEquals("OK READY", resp);
        assertEquals(55L, session.lastBegin);
        assertNotNull(session.getPendingBinaryTarget());
        assertTrue(session.getPendingBinaryTarget().toString().replace('\\', '/').contains("uploads/"));
        assertTrue(session.getPendingBinaryTarget().toString().replace('\\', '/').endsWith("docs/a.txt"));
    }

    @Test
    void filePutShouldRejectTraversal() {
        final var server = new TcpServer(0);
        final var session = new FakeSession();

        final var ctx = new TcpServer.ServerCommandContext(server, new Socket(), "!file.put ../evil.txt 10", session);

        assertThrows(IllegalArgumentException.class, () -> DefaultServerCommands.filePut(ctx));
    }
}
