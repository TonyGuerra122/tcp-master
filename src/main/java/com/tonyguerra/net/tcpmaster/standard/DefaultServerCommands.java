package com.tonyguerra.net.tcpmaster.standard;

import java.net.Socket;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.tonyguerra.net.tcpmaster.configurations.Globals;
import com.tonyguerra.net.tcpmaster.core.TcpServer;
import com.tonyguerra.net.tcpmaster.enums.TcpType;
import com.tonyguerra.net.tcpmaster.handlers.TcpHandler;

/**
 * Built-in server commands shipped with the library.
 *
 * These commands are meant to be overridable by user-defined handlers:
 * if the user registers a handler with the same command string, their handler
 * should win.
 */
public final class DefaultServerCommands {
    // Example: simple rate-limit / anti-spam for broadcast command (optional)
    private static final Set<String> MUTED_CLIENTS = ConcurrentHashMap.newKeySet();

    /**
     * Server health-check
     * 
     * Usage: !ping
     */
    @TcpHandler(command = "!ping", type = TcpType.SERVER)
    public static void ping(Socket client) {
        System.out.println("[SERVER] ping from " + client.getInetAddress() + ":" + client.getPort());
    }

    /**
     * Disconnects the current client sockets.
     * 
     * Usage: !disconnect
     */
    @TcpHandler(command = "!disconnect", type = TcpType.SERVER)
    public static void disconnect(Socket client) {
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * Broadcast a message to all connected clients.
     *
     * Usage: !broadcast Hello everyone
     *
     * NOTE: For this to work with arguments, your TcpServer must parse the command
     * key
     * and also pass the full line to the handler. If you currently pass only
     * "!broadcast",
     * this handler won't receive the message.
     *
     * If you don't have argument support yet, ignore this handler or add parsing
     * first.
     */
    @TcpHandler(command = "!broadcast", type = TcpType.SERVER)
    public static void broadcast(TcpServer server, Socket client, String msg) {
        // Without args support, we can only broadcast a fixed message.
        // If you add args support, change the signature to (TcpServer, Socket, String
        // fullLine) and parse.
        if (MUTED_CLIENTS.contains(clientId(client))) {
            return;
        }

        final String payload = msg.substring("!broadcast".length()).trim();
        if (payload.isEmpty()) {
            return;
        }

        server.broadcast(client, payload);
    }

    /**
     * Mutes broadcasts from a specific client (example of stateful default
     * command).
     *
     * Usage: !mute
     */
    @TcpHandler(command = "!mute", type = TcpType.SERVER)
    public static void mute(Socket client) {
        MUTED_CLIENTS.add(clientId(client));
    }

    /**
     * Unmutes broadcasts from a specific client.
     *
     * Usage: !unmute
     */
    @TcpHandler(command = "!unmute", type = TcpType.SERVER)
    public static void unmute(Socket client) {
        MUTED_CLIENTS.remove(clientId(client));
    }

    @TcpHandler(command = "!bin.begin", type = TcpType.SERVER)
    public static void beginBinary(TcpServer.ServerCommandContext ctx) {
        final String[] parts = ctx.rawLine().trim().split("\\s+");
        if (parts.length < 2) {
            return;
        }

        final long bytes;
        try {
            bytes = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            return;
        }

        if (bytes <= 0) {
            return;
        }

        ctx.session().beginBinary(bytes);
    }

    /**
     * Upload a file to the server.
     *
     * Usage:
     * !file.put <relativePath> <size>
     *
     * Example:
     * !file.put docs/report.pdf 12345
     *
     * After this command, the client MUST send exactly <size> bytes via
     * sendBinary().
     */
    @TcpHandler(command = "!file.put", type = TcpType.SERVER)
    public static String filePut(TcpServer.ServerCommandContext ctx) {
        final String[] parts = ctx.rawLine().trim().split("\\s+");
        if (parts.length < 3) {
            return "ERROR";
        }

        final String relative = parts[1];

        final long size;
        try {
            size = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            return "ERROR";
        }

        if (size <= 0) {
            return "ERROR";
        }

        // Resolve and sanitized path (prevents ../ traversal)
        final var target = safeResolver(Globals.getBaseDirUploads(), relative);

        // Store pending target and switch to binary mode
        final var session = ctx.session();
        session.setPendingBinaryTarget(target);
        session.beginBinary(size);

        return "OK READY";
    }

    private static Path safeResolver(Path baseDir, String userPath) {
        // Remove leading slashes to force "relative"
        String cleanned = userPath.replace('\\', '/');
        while (cleanned.startsWith("/")) {
            cleanned = cleanned.substring(1);
        }

        final var target = baseDir.resolve(cleanned).normalize();

        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path traversal attempt: " + userPath);
        }

        return target;
    }

    private static String clientId(Socket client) {
        return client.getInetAddress() + ":" + client.getPort();
    }
}
