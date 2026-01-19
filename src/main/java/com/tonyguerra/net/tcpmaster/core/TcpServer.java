package com.tonyguerra.net.tcpmaster.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tonyguerra.net.tcpmaster.core.components.LineReader;
import com.tonyguerra.net.tcpmaster.di.Container;
import com.tonyguerra.net.tcpmaster.enums.TcpType;
import com.tonyguerra.net.tcpmaster.errors.TcpException;
import com.tonyguerra.net.tcpmaster.handlers.HandlerRegistry;
import com.tonyguerra.net.tcpmaster.handlers.TcpHandlerScanner;

public final class TcpServer implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServer.class);

    private final int port;

    // command -> handler container (class + method + annotation)
    private final HandlerRegistry registry;

    // Dependency Injection container (used to instantiate handler classes)
    private final Container container;

    // Store connections (not only sockets)
    private final ConcurrentHashMap<Socket, ClientConnection> clients;

    private final AtomicBoolean started;
    private final ExecutorService clientPool;

    private final Object lifecycleLock;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpServer(int port) {
        this.port = port;
        this.registry = new HandlerRegistry();
        this.container = new Container();
        this.clients = new ConcurrentHashMap<>();
        this.started = new AtomicBoolean(false);
        this.clientPool = Executors.newCachedThreadPool();
        this.lifecycleLock = new Object();

        // Optional: allow handlers to request the server instance via DI
        this.container.registerInstance(TcpServer.class, this);

        registry.registerDefault(TcpHandlerScanner.scanDefaults(TcpType.SERVER));
        registry.registerUser(TcpHandlerScanner.scanUserHandlers(TcpType.SERVER));
    }

    public boolean isStarted() {
        return started.get();
    }

    public void start() throws TcpException {
        synchronized (lifecycleLock) {
            if (started.get()) {
                throw new TcpException("Server already started.");
            }

            try {
                serverSocket = new ServerSocket(port);
                started.set(true);

                acceptThread = new Thread(this::acceptLoop, "TcpServer-AccpetThread");
                acceptThread.setDaemon(true);
                acceptThread.start();

                LOGGER.info("✅ Server running on port {}", port);
            } catch (IOException ex) {
                started.set(false);
                safeCloseServerSocket();
                throw new TcpException(ex);
            }
        }
    }

    private void acceptLoop() {
        while (started.get()) {
            try {
                final var client = serverSocket.accept();

                final var conn = new ClientConnection(client);
                clients.put(client, conn);

                LOGGER.info("👤 New client connected: {}:{}", client.getInetAddress(), client.getPort());

                clientPool.submit(() -> handleClient(conn));
            } catch (IOException ex) {
                if (started.get()) {
                    LOGGER.error("⚠️ Accept loop error", ex);
                }
                // If close() closed the ServerSocket, accept() fails and we exit the loop.
                break;
            }
        }
    }

    private void handleClient(ClientConnection conn) {
        try {
            while (true) {

                if (conn.binaryMode) {
                    final long bytes = conn.binaryRemaining;
                    conn.binaryMode = false;
                    conn.binaryRemaining = 0;

                    final var target = conn.getPendingBinaryTarget();
                    conn.setPendingBinaryTarget(null);

                    if (target == null) {
                        // No target defined -> just drain to keep protocol consistent
                        drain(conn, bytes);
                        conn.out.println("ERROR No pending file target");
                        conn.out.flush();
                        continue;
                    }

                    receiveToFile(conn, bytes, target);

                    conn.out.println("OK STORED " + target.getFileName());
                    conn.out.flush();
                    continue;
                }

                final String message = conn.lineReader.readLineUtf8();
                if (message == null)
                    break;

                LOGGER.info("📨 Received from {}: {}", conn.id(), message);

                if (message.startsWith("!")) {
                    final String commandKey = extractCommandKey(message);
                    final String response = handleCommand(commandKey, message, conn);
                    conn.out.println(response);
                    conn.out.flush();
                    continue;
                }

                conn.out.println("OK");
                conn.out.flush();
            }
        } catch (IOException ex) {
            LOGGER.warn("⚠️ Client communication error {}: {}", conn.id(), ex.getMessage());
        } finally {
            removeClient(conn.socket);
        }
    }

    private static void receiveToFile(ClientConnection conn, long bytes, Path target) throws IOException {
        Files.createDirectories(target.getParent());

        try (final var fileOut = Files.newOutputStream(target)) {
            final byte[] buf = new byte[8192];
            long remaining = bytes;

            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read = conn.rawIn.read(buf, 0, toRead);
                if (read == -1)
                    throw new IOException("Stream closed while receiving file");
                fileOut.write(buf, 0, read);
                remaining -= read;
            }
            fileOut.flush();
        }
    }

    private static void drain(ClientConnection conn, long bytes) throws IOException {
        final byte[] buf = new byte[8192];
        long remaining = bytes;

        while (remaining > 0) {
            final int toRead = (int) Math.min(buf.length, remaining);
            final int read = conn.rawIn.read(buf, 0, toRead);
            if (read == -1) {
                throw new IOException("Stream closed while reading binary payload");
            }
            remaining -= read;
        }
    }

    private static String extractCommandKey(String raw) {
        if (raw == null)
            return "";
        String s = raw.trim();
        if (s.isEmpty())
            return "";
        int space = s.indexOf(' ');
        return (space >= 0) ? s.substring(0, space) : s;
    }

    private String handleCommand(String commandKey, String fullLine, ClientConnection conn) {
        final var def = registry.resolve(commandKey);
        if (def == null)
            return "Unknown command: " + commandKey;

        final Method method = def.method();

        try {
            final Object target = Modifier.isStatic(method.getModifiers())
                    ? null
                    : container.get(def.ownerClass());

            final Object[] args = resolveArguments(method, conn, fullLine);
            method.invoke(target, args);

            if (conn.socket.isClosed())
                return "Connection closed by handler";
            return "Handler executed successfully: " + method.getName();

        } catch (IllegalArgumentException ex) {
            LOGGER.warn("⚠️ Invalid handler parameters for {}#{}: {}",
                    def.ownerClass().getName(), method.getName(), ex.getMessage());
            return "Invalid handler parameters: " + ex.getMessage();

        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.error("❌ Error executing handler {}", method.getName(), ex);
            return "Error executing handler: " + ex.getMessage();
        }
    }

    public void broadcast(Socket sender, String message) {
        final String senderId = sender.getInetAddress() + ":" + sender.getPort();
        final String payload = String.format("[BROADCAST] %s -> %s", senderId, message);

        clients.forEach((sock, conn) -> {
            if (sock.isClosed()) {
                removeClient(sock);
                return;
            }

            if (sock.getPort() == sender.getPort() && sock.getInetAddress().equals(sender.getInetAddress())) {
                return;
            }

            try {
                conn.out.println(payload);
                conn.out.flush();
            } catch (Exception ex) {
                LOGGER.warn("⚠️ Broadcast failed to {}: {}", conn.id(), ex.getMessage());
                removeClient(sock);
            }
        });

    }

    private void removeClient(Socket client) {
        final var conn = clients.remove(client);
        if (conn == null)
            return;

        try {
            conn.close();
        } catch (IOException ex) {
            LOGGER.warn("⚠️ Error closing client {}: {}", conn.id(), ex.getMessage());
        } finally {
            LOGGER.info("🧹 Client removed: {}", conn.id());
        }
    }

    private void removeAllClients() {
        clients.keySet().forEach(this::removeClient);
    }

    private void safeCloseServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        serverSocket = null;
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (!started.compareAndSet(true, false)) {
                return;
            }

            safeCloseServerSocket();
            removeAllClients();
            clientPool.shutdownNow();
            LOGGER.info("🔌 Server stopped.");
        }
    }

    private Object[] resolveArguments(Method method, ClientConnection conn, String fullLine) {
        final var params = method.getParameterTypes();
        final var args = new Object[params.length];

        final var ctx = new ServerCommandContext(this, conn.socket, fullLine, conn);

        for (int i = 0; i < params.length; i++) {
            if (params[i] == TcpServer.class) {
                args[i] = this;
            } else if (params[i] == Socket.class) {
                args[i] = conn.socket;
            } else if (params[i] == String.class) {
                args[i] = fullLine;
            } else if (params[i] == ServerCommandContext.class) {
                args[i] = ctx;
            } else if (params[i] == TcpSession.class) {
                args[i] = conn;
            } else {
                throw new IllegalArgumentException("Unsupported parameter type: " + params[i].getName());
            }
        }

        return args;
    }

    public record ServerCommandContext(TcpServer server, Socket socket, String rawLine, TcpSession session) {
    }

    private static final class ClientConnection implements Closeable, TcpSession {
        private final Socket socket;

        private final InputStream rawIn;
        private final OutputStream rawOut;
        private final LineReader lineReader;

        private final PrintWriter out;

        // Future: binary mode state (for file transfer)
        private volatile boolean binaryMode;
        private volatile long binaryRemaining;

        private volatile Path pendingBinaryPath;

        private ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.rawIn = socket.getInputStream();
            this.rawOut = socket.getOutputStream();
            this.lineReader = new LineReader(rawIn);
            this.out = new PrintWriter(rawOut, true);
            this.binaryMode = false;
            this.binaryRemaining = 0;
        }

        private String id() {
            return socket.getInetAddress() + ":" + socket.getPort();
        }

        @Override
        public void close() throws IOException {
            try {
                rawIn.close();
            } catch (Exception ignored) {
            }
            try {
                rawOut.close();
            } catch (Exception ignored) {
            }
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }

        @Override
        public Socket socket() {
            return socket;
        }

        @Override
        public InputStream in() {
            return rawIn;
        }

        @Override
        public OutputStream out() {
            return rawOut;
        }

        @Override
        public void beginBinary(long bytes) {
            binaryMode = true;
            binaryRemaining = bytes;
        }

        @Override
        public boolean isBinaryMode() {
            return binaryMode;
        }

        @Override
        public void setPendingBinaryTarget(Path target) {
            pendingBinaryPath = target;
        }

        @Override
        public Path getPendingBinaryTarget() {
            return pendingBinaryPath;
        }
    }
}
