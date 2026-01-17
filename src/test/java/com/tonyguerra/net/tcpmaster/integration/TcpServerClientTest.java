package com.tonyguerra.net.tcpmaster.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.tonyguerra.net.tcpmaster.core.TcpClient;
import com.tonyguerra.net.tcpmaster.core.TcpServer;

final class TcpServerClientTest {

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
    void clientShouldConnectAndPing() throws Exception {
        final int port = freePort();

        server = new TcpServer(port);
        server.start();

        try (TcpClient client = new TcpClient("127.0.0.1", port)) {
            client.setResponseTimeoutMs(2000);
            client.connect();

            // Do not run client-local handlers for this test
            final String resp = client.sendMessage("!ping", false);

            assertNotNull(resp);
            assertTrue(resp.toLowerCase().contains("executed")
                    || resp.toLowerCase().contains("success")
                    || resp.toLowerCase().contains("handler"),
                    "Unexpected response: " + resp);
        }
    }

    @Test
    void normalMessageShouldReturnOk() throws Exception {
        final int port = freePort();

        server = new TcpServer(port);
        server.start();

        try (final var client = new TcpClient("127.0.0.1", port)) {
            client.setResponseTimeoutMs(2000);
            client.connect();

            final String resp = client.sendMessage("hello", false);
            assertEquals("OK", resp);
        }
    }
}
