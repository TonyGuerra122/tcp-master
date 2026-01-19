package com.tonyguerra.net.tcpmaster.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.tonyguerra.net.tcpmaster.core.TcpClient;
import com.tonyguerra.net.tcpmaster.core.TcpServer;

final class FileUploadIntegrationTest {
    private TcpServer server;

    private static int freePort() throws IOException {
        try (final var ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldUploadFileAndStoreItAndReportProgress() throws Exception {
        final int port = freePort();

        server = new TcpServer(port);
        server.start();

        final var tmpFile = Files.createTempFile("tcp-master-upload-", ".bin");
        final byte[] content = new byte[32_000];
        for (int i = 0; i < content.length; i++)
            content[i] = (byte) (i % 251);
        Files.write(tmpFile, content);

        final String remoteName = "test-" + UUID.randomUUID() + ".bin";

        final var lastSent = new AtomicLong(0);

        try (TcpClient client = new TcpClient("127.0.0.1", port)) {
            client.setResponseTimeoutMs(5000);
            client.connect();

            final String confirm = client.uploadFile(tmpFile, remoteName, (sent, total) -> {
                assertTrue(sent >= lastSent.get());
                lastSent.set(sent);
                assertEquals(content.length, total);
            });

            assertNotNull(confirm);
            assertTrue(confirm.startsWith("OK STORED"), "Unexpected confirm: " + confirm);
        }

        // Default base dir from your command: uploads/
        final var stored = Path.of("uploads").toAbsolutePath().resolve(remoteName).normalize();

        assertTrue(Files.exists(stored), "Stored file not found: " + stored);
        assertArrayEquals(content, Files.readAllBytes(stored));

        // cleanup
        Files.deleteIfExists(stored);
        Files.deleteIfExists(tmpFile);
    }
}
