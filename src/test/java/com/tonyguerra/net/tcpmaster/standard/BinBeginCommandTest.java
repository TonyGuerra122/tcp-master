package com.tonyguerra.net.tcpmaster.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tonyguerra.net.tcpmaster.core.TcpServer;
import com.tonyguerra.net.tcpmaster.core.TcpSession;

final class BinBeginCommandTest {
    static final class FakeSession implements TcpSession {
        long lastBegin = -1;

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
            throw new UnsupportedOperationException("Unimplemented method 'setPendingBinaryTarget'");
        }

        @Override
        public Path getPendingBinaryTarget() {
            throw new UnsupportedOperationException("Unimplemented method 'getPendingBinaryTarget'");
        }
    }

    @Test
    void binBeginShouldEnableBinaryModeWithParsedBytes() {
        final var server = new TcpServer(0); // no start() needed
        final var session = new FakeSession();
        final var socket = new Socket();

        final var ctx = new TcpServer.ServerCommandContext(server, socket, "!bin.begin 1234", session);

        DefaultServerCommands.beginBinary(ctx);

        assertEquals(1234L, session.lastBegin);
        assertTrue(session.isBinaryMode());
    }

    @Test
    void binBeginShouldIgnoreWhenMissingArgument() {
        final var server = new TcpServer(0);
        final var session = new FakeSession();
        final var socket = new Socket();

        final var ctx = new TcpServer.ServerCommandContext(server, socket, "!bin.begin", session);

        DefaultServerCommands.beginBinary(ctx);

        assertEquals(-1L, session.lastBegin);
        assertFalse(session.isBinaryMode());
    }

    @Test
    void binBeginShouldIgnoreWhenNotANumber() {
        final var server = new TcpServer(0);
        final var session = new FakeSession();
        final var socket = new Socket();

        final var ctx = new TcpServer.ServerCommandContext(server, socket, "!bin.begin abc", session);

        DefaultServerCommands.beginBinary(ctx);

        assertEquals(-1L, session.lastBegin);
        assertFalse(session.isBinaryMode());
    }

    @Test
    void binBeginShouldIgnoreWhenZeroOrNegative() {
        final var server = new TcpServer(0);
        final var session = new FakeSession();
        final var socket = new Socket();

        DefaultServerCommands.beginBinary(new TcpServer.ServerCommandContext(server, socket, "!bin.begin 0", session));
        assertEquals(-1L, session.lastBegin);

        DefaultServerCommands
                .beginBinary(new TcpServer.ServerCommandContext(server, socket, "!bin.begin -10", session));
        assertEquals(-1L, session.lastBegin);
    }
}
