package com.tonyguerra.net.tcpmaster.core;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tonyguerra.net.tcpmaster.di.Container;
import com.tonyguerra.net.tcpmaster.enums.TcpType;
import com.tonyguerra.net.tcpmaster.errors.TcpException;
import com.tonyguerra.net.tcpmaster.handlers.HandlerDefinition;
import com.tonyguerra.net.tcpmaster.handlers.HandlerRegistry;
import com.tonyguerra.net.tcpmaster.handlers.TcpHandlerScanner;

public final class TcpClient implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpClient.class);

    private final String ip;
    private final int port;

    // command -> handler definition (class + method + annotation)
    private final HandlerRegistry registry;

    // Dependency Injection container (used to instantiate handler classes)
    private final Container container;

    private final AtomicBoolean connected;
    private final Object lifecycleLock;

    // Responses (request/response serial model)
    private final BlockingQueue<String> responses;

    // Listeners
    private final CopyOnWriteArrayList<ConnectionListener> connectionListeners;
    private final CopyOnWriteArrayList<MessageListener> messageListeners;
    private final CopyOnWriteArrayList<BroadcastListener> broadcastListeners;

    // Where listener callbacks are executed (GUI can provide a UI-thread
    // dispatcher)
    private volatile Executor eventDispatcher;

    // Async worker (for send/connect wrappers)
    private final ExecutorService asyncExecutor;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread readerThread;

    // Config
    private volatile long responseTimeoutMs;
    private volatile boolean logNonBroadcastMessages;

    private volatile ClientCommandPolicy commandPolicy;

    public TcpClient(String ip, int port) {
        this.ip = Objects.requireNonNull(ip, "ip");
        this.port = port;
        this.container = new Container();
        this.connected = new AtomicBoolean(false);
        this.lifecycleLock = new Object();
        this.responses = new LinkedBlockingQueue<>();
        this.registry = new HandlerRegistry();
        this.connectionListeners = new CopyOnWriteArrayList<>();
        this.messageListeners = new CopyOnWriteArrayList<>();
        this.broadcastListeners = new CopyOnWriteArrayList<>();
        this.eventDispatcher = Runnable::run; // default: same thread
        this.asyncExecutor = Executors.newCachedThreadPool();
        this.responseTimeoutMs = 10_000;
        this.logNonBroadcastMessages = true;
        this.commandPolicy = ClientCommandPolicy.LOCAL_ONLY;

        // Register defaults shipped with the lib
        registry.registerDefault(TcpHandlerScanner.scanDefaults(TcpType.CLIENT));

        // Register user-defined handlers (application code)
        registry.registerUser(TcpHandlerScanner.scanUserHandlers(TcpType.CLIENT));

        // Allow handlers to request the client instance via DI
        this.container.registerInstance(TcpClient.class, this);
    }

    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Sets the executor used to dispatch events to listeners.
     * In JavaFX you can pass: r -> Platform.runLater(r)
     * In Swing you can pass: r -> SwingUtilities.invokeLater(r)
     */
    public TcpClient setEventDispatcher(Executor dispatcher) {
        this.eventDispatcher = (dispatcher != null) ? dispatcher : Runnable::run;
        return this;
    }

    public TcpClient setResponseTimeoutMs(long timeoutMs) {
        if (timeoutMs <= 0)
            throw new IllegalArgumentException("timeoutMs must be > 0");
        this.responseTimeoutMs = timeoutMs;
        return this;
    }

    public TcpClient setLogNonBroadcastMessages(boolean enabled) {
        this.logNonBroadcastMessages = enabled;
        return this;
    }

    // Listener registration
    public TcpClient addConnectionListener(ConnectionListener l) {
        if (l != null)
            connectionListeners.add(l);
        return this;
    }

    public TcpClient removeConnectionListener(ConnectionListener l) {
        if (l != null)
            connectionListeners.remove(l);
        return this;
    }

    public TcpClient addMessageListener(MessageListener l) {
        if (l != null)
            messageListeners.add(l);
        return this;
    }

    public TcpClient removeMessageListener(MessageListener l) {
        if (l != null)
            messageListeners.remove(l);
        return this;
    }

    public TcpClient addBroadcastListener(BroadcastListener l) {
        if (l != null)
            broadcastListeners.add(l);
        return this;
    }

    public TcpClient removeBroadcastListener(BroadcastListener l) {
        if (l != null)
            broadcastListeners.remove(l);
        return this;
    }

    public TcpClient setCommandPolicy(ClientCommandPolicy policy) {
        if (policy == null)
            throw new IllegalArgumentException("policy must not be null");
        this.commandPolicy = policy;
        return this;
    }

    // -------------------------
    // Sync API (safe for non-UI threads)
    // -------------------------

    public void connect() throws TcpException {
        synchronized (lifecycleLock) {
            if (connected.get())
                throw new TcpException("Client is already connected.");

            try {
                socket = new Socket(ip, port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(socket.getOutputStream(), true);

                connected.set(true);

                readerThread = new Thread(this::readLoop, "TcpClient-ReaderThread");
                readerThread.setDaemon(true);
                readerThread.start();

                LOGGER.info("✅ Connected to server: {}:{}", ip, port);
                fireConnected();
            } catch (IOException ex) {
                safeCloseQuietly();
                fireError(ex);
                throw new TcpException(ex);
            }
        }
    }

    public String sendMessage(String message) throws TcpException {
        return sendMessage(message, true);
    }

    public String sendMessage(String message, boolean readCommand) throws TcpException {
        if (!connected.get())
            throw new TcpException("No Server Connected");
        if (message == null)
            throw new IllegalArgumentException("message must not be null");

        try {
            final String trimmed = message.trim();
            if (trimmed.isEmpty())
                return "OK";

            final String commandKey = extractCommandKey(trimmed);

            var outcome = new LocalCommandOutcome(false, null);

            if (readCommand && commandPolicy != ClientCommandPolicy.REMOTE_ONLY) {
                outcome = handleCommandIfExists(commandKey, trimmed);
            }

            // 1) If handler returned explicit decision, obey it
            if (outcome.decision() != null) {
                if (outcome.decision() instanceof ClientCommandResult.DontSend d) {
                    return d.localResponse().orElse("OK (handled locally)");
                }
                if (outcome.decision() instanceof ClientCommandResult.Send s) {
                    return sendRawToServer(s.messageToSend());
                }
            }

            // 2) No explicit decision: apply policy
            if (outcome.handled()) {
                if (commandPolicy == ClientCommandPolicy.LOCAL_ONLY) {
                    return "OK (handled locally)";
                }
                // LOCAL_AND_REMOTE -> send original
                // REMOTE_ONLY doesn't reach here
            }

            return sendRawToServer(trimmed);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TcpException("⚠️ Interrupted while waiting response.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new TcpException(ex);
        }
    }

    private String sendRawToServer(String msg) throws TcpException, InterruptedException {
        synchronized (lifecycleLock) {
            if (!connected.get() || out == null)
                throw new TcpException("No Server Connected");
            out.println(msg);
            out.flush();
        }
        final String response = responses.poll(responseTimeoutMs, TimeUnit.MILLISECONDS);
        if (response == null)
            throw new TcpException("Timeout waiting server response");
        return response;
    }

    public void sendBinary(InputStream data, long size) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        if (!connected.get()) {
            throw new IOException("Client not connected");
        }

        // IMPORTANT: Prevent text writes while binary is being sent
        synchronized (lifecycleLock) {
            if (socket == null || socket.isClosed()) {
                throw new IOException("Socket is closed");
            }

            final OutputStream outStream = socket.getOutputStream();

            final byte[] buffer = new byte[8192];
            long remaining = size;

            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = data.read(buffer, 0, toRead);

                if (read == -1) {
                    throw new IOException(
                            "InputStream ended before sending expected " + size + " bytes");
                }

                outStream.write(buffer, 0, read);
                remaining -= read;
            }

            outStream.flush();
        }
    }

    public String uploadFile(Path localFile, String remotePath) throws TcpException, IOException {
        if (localFile == null) {
            throw new IllegalArgumentException("localFile must not be null");
        }

        if (remotePath == null || remotePath.isBlank()) {
            throw new IllegalArgumentException("remotePath must not be null/blank");
        }

        // Optional: keep protocol simple
        if (remotePath.contains(" ")) {
            throw new IllegalArgumentException("remotePath must not contain spaces");
        }

        if (!Files.exists(localFile) || !Files.isRegularFile(localFile)) {
            throw new IOException("Local file not found or not a regular file: " + localFile);
        }

        if (!connected.get()) {
            throw new TcpException("No Server Connected");
        }

        final long size = Files.size(localFile);
        if (size <= 0) {
            throw new IOException("File is empty or size is invalid: " + localFile);
        }

        // 1) Tell server what is coming (NO extra "size" token)
        final String initResp = sendMessage(String.format("!file.put %s %d", remotePath, size), false);

        // Only keep this check if your server actually returns "OK ..."
        // if (!initResp.startsWith("OK")) {
        // throw new TcpException("Server refused upload: " + initResp);
        // }

        // 2) Send bytes
        try (InputStream is = Files.newInputStream(localFile)) {
            sendBinary(is, size);
        }

        // 3) Read confirmation (OK STORED ...)
        return readNextResponse();
    }

    public String uploadFile(Path localFile) throws TcpException, IOException {
        return uploadFile(localFile, localFile.getFileName().toString());
    }

    public String uploadFile(Path localFile) throws TcpException, IOException {
        return uploadFile(localFile, localFile.getFileName().toString());
    }

    public String readNextResponse(long timeoutMs) throws TcpException {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }

        if (!connected.get()) {
            throw new TcpException("No Server Connected");
        }

        try {
            final String response = responses.poll(timeoutMs, TimeUnit.MILLISECONDS);

            if (response == null) {
                throw new TcpException("Timeout waiting server response");
            }

            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TcpException("Interrupted while waiting server response");
        }
    }

    public String readNextResponse() throws TcpException {
        return readNextResponse(responseTimeoutMs);
    }

    public void disconnect() throws TcpException {
        synchronized (lifecycleLock) {
            if (!connected.compareAndSet(true, false))
                return;

            try {
                if (out != null) {
                    try {
                        out.println("!disconnect");
                        out.flush();
                    } catch (Exception ignored) {
                    }
                }
                safeCloseQuietly();
            } catch (Exception ex) {
                fireError(ex);
                throw new TcpException(ex);
            } finally {
                // Unblock any waiting sendMessage()
                responses.offer("🔌 Client disconnected.");
                LOGGER.info("🔌 Client disconnected.");
                fireDisconnected();
            }
        }
    }

    // -------------------------
    // Async API (GUI-friendly)
    // -------------------------

    public CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                connect();
            } catch (TcpException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<String> sendMessageAsync(String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendMessage(message);
            } catch (TcpException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> disconnectAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                disconnect();
            } catch (TcpException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    // -------------------------
    // Internals
    // -------------------------

    /**
     * Executes a registered client handler if the command matches exactly.
     *
     * Supported signatures:
     * - (TcpClient)
     * - ()
     *
     * Returns true if a handler existed for this command.
     */
    private LocalCommandOutcome handleCommandIfExists(String commandKey, String fullLine)
            throws IllegalAccessException, InvocationTargetException {

        final HandlerDefinition def = registry.resolve(commandKey);
        if (def == null)
            return new LocalCommandOutcome(false, null);

        final var method = def.method();
        final Object target = Modifier.isStatic(method.getModifiers())
                ? null
                : container.get(def.ownerClass());

        Object ret = null;

        if (method.getParameterCount() == 2
                && method.getParameterTypes()[0] == TcpClient.class
                && method.getParameterTypes()[1] == String.class) {
            ret = method.invoke(target, this, fullLine);

        } else if (method.getParameterCount() == 1
                && method.getParameterTypes()[0] == TcpClient.class) {
            ret = method.invoke(target, this);

        } else if (method.getParameterCount() == 0) {
            ret = method.invoke(target);

        } else {
            LOGGER.warn("⚠️ Invalid handler signature: {}#{}", def.ownerClass().getName(), method.getName());
            return new LocalCommandOutcome(true, ClientCommandResult.dontSend("Invalid handler signature"));
        }

        if (ret instanceof ClientCommandResult r) {
            return new LocalCommandOutcome(true, r);
        }

        // Handler existed, but returned no explicit decision
        return new LocalCommandOutcome(true, null);
    }

    /**
     * The ONLY place that reads from the socket.
     * Routes:
     * - broadcast -> broadcastListeners
     * - normal -> messageListeners + responses queue
     */
    private void readLoop() {
        try {
            String line;
            while (connected.get() && (line = in.readLine()) != null) {
                if (line.contains("[BROADCAST]")) {
                    LOGGER.info("📢 {}", line);
                    fireBroadcast(line);
                } else {
                    if (logNonBroadcastMessages)
                        LOGGER.info("📩 Server Message: {}", line);
                    fireMessage(line);
                    responses.offer(line);
                }
            }

            if (connected.get()) {
                LOGGER.warn("🔌 Connection closed by the server.");
            }
        } catch (SocketTimeoutException ex) {
            LOGGER.warn("⏳ Socket read timeout: {}", ex.getMessage());
            fireError(ex);
        } catch (IOException ex) {
            if (connected.get()) {
                LOGGER.warn("⚠️ Error receiving messages: {}", ex.getMessage());
                fireError(ex);
            }
        } finally {
            try {
                disconnect();
            } catch (TcpException ex) {
                LOGGER.error("❌ Error disconnecting after readLoop failure: {}", ex.getMessage());
            }
        }
    }

    private static String extractCommandKey(String raw) {
        if (raw == null)
            return "";
        final String s = raw.trim();
        if (s.isEmpty())
            return "";

        // command key is first token (splits on whitespace)
        final int space = s.indexOf(' ');
        return (space >= 0) ? s.substring(0, space) : s;
    }

    private void safeCloseQuietly() {
        try {
            if (in != null)
                in.close();
        } catch (Exception ignored) {
        }
        try {
            if (out != null)
                out.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }

        in = null;
        out = null;
        socket = null;
    }

    // -------------------------
    // Event helpers (dispatched via eventDispatcher)
    // -------------------------

    private void fireConnected() {
        dispatch(() -> connectionListeners.forEach(l -> {
            try {
                l.onConnected();
            } catch (Exception ignored) {
            }
        }));
    }

    private void fireDisconnected() {
        dispatch(() -> connectionListeners.forEach(l -> {
            try {
                l.onDisconnected();
            } catch (Exception ignored) {
            }
        }));
    }

    private void fireError(Throwable t) {
        dispatch(() -> connectionListeners.forEach(l -> {
            try {
                l.onError(t);
            } catch (Exception ignored) {
            }
        }));
    }

    private void fireMessage(String msg) {
        dispatch(() -> messageListeners.forEach(l -> {
            try {
                l.onMessage(msg);
            } catch (Exception ignored) {
            }
        }));
    }

    private void fireBroadcast(String msg) {
        dispatch(() -> broadcastListeners.forEach(l -> {
            try {
                l.onBroadcast(msg);
            } catch (Exception ignored) {
            }
        }));
    }

    private void dispatch(Runnable r) {
        try {
            eventDispatcher.execute(r);
        } catch (Exception ex) {
            // Fallback: never lose events completely
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() {
        try {
            disconnect();
        } catch (TcpException ex) {
            ex.printStackTrace();
        } finally {
            asyncExecutor.shutdownNow();
        }
    }

    // -------------------------
    // Listener interfaces
    // -------------------------

    public interface ConnectionListener {
        void onConnected();

        void onDisconnected();

        void onError(Throwable error);
    }

    @FunctionalInterface
    public interface MessageListener {
        void onMessage(String message);
    }

    @FunctionalInterface
    public interface BroadcastListener {
        void onBroadcast(String message);
    }

    private record LocalCommandOutcome(boolean handled, ClientCommandResult decision) {
    }
}
