package com.tonyguerra.net.tcpmaster.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.tonyguerra.net.tcpmaster.core.TcpClient;
import com.tonyguerra.net.tcpmaster.core.TcpServer;

final class BroadcastTest {

    private TcpServer server;

    private static int freePort() throws IOException {
        try (final var ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null)
            server.close();
    }

    @Test
    void broadcastShouldReachOtherClients() throws Exception {
        final int port = freePort();

        server = new TcpServer(port);
        server.start();

        final var receivedByB = new ArrayBlockingQueue<String>(10);

        try (final var a = new TcpClient("127.0.0.1", port);
                final var b = new TcpClient("127.0.0.1", port)) {

            a.setResponseTimeoutMs(2000).connect();
            b.setResponseTimeoutMs(2000).connect();

            b.addBroadcastListener(receivedByB::offer);

            // This assumes you have a server command handler "!broadcast"
            final String resp = a.sendMessage("!broadcast hello", false);
            assertNotNull(resp);

            final String broadcast = receivedByB.poll(2, TimeUnit.SECONDS);
            assertNotNull(broadcast, "Client B should receive a broadcast");
            assertTrue(broadcast.contains("[BROADCAST]"), "Unexpected broadcast: " + broadcast);
            assertTrue(broadcast.contains("hello"), "Unexpected broadcast: " + broadcast);
        }
    }
}
