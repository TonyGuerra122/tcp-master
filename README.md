# 🚀 tcp-master

> A lightweight **TCP client/server library** for Java 21+ with **command-based handlers**, **annotation scanning**, and **simple dependency injection**.

[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk21-archive.html)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-0.1.0-brightgreen)](https://mvnrepository.com)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE.md)
[![Tests](https://img.shields.io/badge/Tests-16%2F16%20Passing-success)]()

### ✨ Designed to be:
- 💻 Easy to embed in **CLI**, **GUI**, or **desktop applications**
- 🔧 Extensible via **annotations**
- 📦 Safe as a **library** (no forced logging bindings)
- 🎯 Friendly to **Java 21+** and **modular projects**
- ⚡ Production-ready with **zero dependencies** (beyond SLF4J API)

---

## 📚 Quick Navigation
- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Features](#-features)
- [Concepts](#-basic-concepts)
- [Examples](#-examples)
- [Async API](#-async--gui-friendly-api)
- [Network Usage](#-network-usage)
- [Threading](#-threading-model)
- [FAQ](#-frequently-asked-questions)

---

## ✨ Features

| Feature | Status | Details |
|---------|--------|----------|
| 🌐 **TCP Server & Client** | ✅ | Async, thread-safe, graceful shutdown |
| 🏷️ **Annotation-based handlers** | ✅ | `@TcpHandler` with command routing |
| 🎮 **Command system** | ✅ | Built-in + custom, user-overridable |
| 📡 **Broadcast support** | ✅ | Send to all clients or selective mute |
| 🧩 **DI Container** | ✅ | Simple, lightweight, zero-config |
| 📨 **Listeners API** | ✅ | ConnectionListener, MessageListener, BroadcastListener |
| 🔄 **Async APIs** | ✅ | Perfect for GUI frameworks (Swing, JavaFX) |
| 📁 **Binary file transfer** | ✅ | Upload/download with progress callbacks |
| 🔐 **Thread-safe** | ✅ | ConcurrentHashMap, AtomicBoolean, BlockingQueue |
| 🛠️ **No dependencies** | ✅ | Only SLF4J API (you choose impl) |
| 📦 **Maven Central** | ✅ | Ready to use in your projects |

---

## 📦 Installation

### Maven Central

```xml
<dependency>
    <groupId>io.github.tonyguerra122</groupId>
    <artifactId>tcp-master</artifactId>
    <version>0.1.1</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'io.github.tonyguerra122:tcp-master:0.1.1'
}
```

### Java Module (optional)

```java
module com.example.app {
    requires com.tonyguerra.net.tcpmaster;
}
```

---

## ⚡ Quick Start

### Server (5 lines)

```java
TcpServer server = new TcpServer(9999);
server.start();

scan(ServerHandlers.class);  // Register your handlers
System.out.println("✅ Server running on port 9999");
Thread.currentThread().join();  // Keep running
```

### Client (5 lines)

```java
TcpClient client = new TcpClient("127.0.0.1", 9999);
client.connect();

String response = client.sendMessage("!ping");
System.out.println("📨 Response: " + response);
client.disconnect();
```

---

## 🎯 Basic Concepts

### 📝 Commands

Commands are **plain text messages** starting with `!`.

```
!ping                           ← Health check
!broadcast hello world          ← Message everyone
!disconnect                     ← Close connection
!custom-command arg1 arg2       ← Your custom command
```

Each command is routed to a **handler method** using `@TcpHandler` annotation.

### 🎮 Built-in Commands

The server ships with these built-in commands (can be overridden):

| Command | Type | Description | Example |
|---------|------|-------------|----------|
| `!ping` | SERVER | Health check | `!ping` → `PONG` |
| `!disconnect` | CLIENT | Close connection | `!disconnect` |
| `!broadcast` | SERVER | Send to all clients | `!broadcast Hello everyone` |
| `!mute` | SERVER | Mute broadcasts from client | `!mute` |
| `!unmute` | SERVER | Re-enable broadcasts | `!unmute` |

### ⚖️ Override Rules

```
1. User-defined handlers WIN (highest priority)
2. Built-in defaults are used as FALLBACK
3. Same command = user handler overrides built-in
```

---

## 💻 Examples

### 🖥️ Server with Custom Handler

```java
package com.example.server;

import java.net.Socket;
import com.tonyguerra.net.tcpmaster.annotations.TcpHandler;
import com.tonyguerra.net.tcpmaster.enums.TcpType;
import com.tonyguerra.net.tcpmaster.core.TcpServer;

public class ServerHandlers {
    
    @TcpHandler(command = "!ping", type = TcpType.SERVER)
    public void ping(Socket client) {
        System.out.println("🏓 Ping from " + client.getInetAddress());
    }
    
    @TcpHandler(command = "!echo", type = TcpType.SERVER)
    public String echo(Socket client, String message) {
        return "📢 Echo: " + message;
    }
    
    @TcpHandler(command = "!whoami", type = TcpType.SERVER)
    public String whoami(Socket client) {
        return "👤 You are: " + client.getInetAddress();
    }
}

// Usage:
TcpServer server = new TcpServer(9999);
server.start();
System.out.println("✅ Server listening on port 9999");
```

**Supported server handler signatures:**
```java
()                              // No args
(TcpServer)                     // Server instance
(Socket)                        // Client socket
(TcpServer, Socket)             // Server + socket
(String)                        // Message only
(Socket, String)                // Socket + message
(TcpServer, Socket, String)     // All three
```

---

### 💻 Client with Local Command Handler

```java
package com.example.client;

import com.tonyguerra.net.tcpmaster.annotations.TcpHandler;
import com.tonyguerra.net.tcpmaster.core.ClientCommandResult;
import com.tonyguerra.net.tcpmaster.enums.TcpType;

public class ClientHandlers {
    
    @TcpHandler(command = "!time", type = TcpType.CLIENT)
    public ClientCommandResult time() {
        // Handled locally - NOT sent to server
        long now = System.currentTimeMillis();
        System.out.println("🕐 Local time: " + now);
        return ClientCommandResult.dontSend("Time handled locally");
    }
    
    @TcpHandler(command = "!hi", type = TcpType.CLIENT)
    public ClientCommandResult greet() {
        System.out.println("👋 Hello from client!");
        return ClientCommandResult.dontSend();
    }
}

// Usage:
TcpClient client = new TcpClient("192.168.1.10", 9999);
client.connect();

String result = client.sendMessage("!time");     // Handled locally
String echo = client.sendMessage("!ping");       // Sent to server

client.disconnect();
```

**Supported client handler signatures:**
```java
()                      // No args
(TcpClient)             // Client instance
(TcpClient, String)     // Client + message
```

---

### 📡 Broadcast Example

```java
@TcpHandler(command = "!broadcast", type = TcpType.SERVER)
public void broadcast(TcpServer server, Socket sender, String message) {
    // Extract message content
    String content = message.substring("!broadcast".length()).trim();
    
    // Send to everyone except sender
    server.broadcast(sender, "📢 " + content);
}
```

**Client receives:**
```
[BROADCAST] /192.168.1.100:52341 -> 📢 Hello everyone!
```

---

## 📁 File Transfer (Binary Mode)

**tcp-master** supports **binary file transfer** over the same TCP connection without switching protocols.

### 📤 Upload (Synchronous)

```java
Path file = Path.of("document.pdf");

// Upload with progress callback
String serverResponse = client.uploadFile(
    file,                        // File to upload
    "document.pdf",              // Destination name
    (sent, total) -> {           // Progress callback
        int percent = (int)((sent * 100) / total);
        System.out.printf("\rUploading... %d%%", percent);
    }
);

System.out.println("\n✅ Server: " + serverResponse);
```

### ⚡ Upload (Asynchronous)

```java
client.uploadFileAsync(
    Path.of("document.pdf"),
    "document.pdf",
    (sent, total) -> {
        int percent = (int)((sent * 100) / total);
        System.out.printf("\r📤 Uploading... %d%%", percent);
    }
)
.thenAccept(response -> {
    System.out.println("\n✅ Server: " + response);
})
.exceptionally(ex -> {
    System.err.println("❌ Upload failed: " + ex.getMessage());
    return null;
});
```

### 🎯 Server-side File Handler

```java
@TcpHandler(command = "!upload", type = TcpType.SERVER)
public String handleFileUpload(TcpServer server, Socket client, String metadata) {
    // Parse metadata to get filename
    String filename = metadata.split(":")[1];
    
    // Save file (implementation depends on your use case)
    Path destination = Path.of("uploads/", filename);
    
    return "✅ File saved to " + destination.toString();
}
```

---

## 🔄 Async & GUI-Friendly API

Designed to work seamlessly with **Swing**, **JavaFX**, or other UI frameworks.

### JavaFX Example

```java
import javafx.application.Platform;
import com.tonyguerra.net.tcpmaster.core.TcpClient;

public class ChatUI {
    private TcpClient client;
    
    public void connectToServer() {
        client = new TcpClient("192.168.1.10", 9999);
        
        // Dispatch all callbacks to JavaFX thread
        client.setEventDispatcher(r -> Platform.runLater(r));
        
        // Connect asynchronously
        client.connectAsync()
              .thenRun(() -> {
                  statusLabel.setText("✅ Connected");
                  updateUI();
              })
              .exceptionally(ex -> {
                  statusLabel.setText("❌ Connection failed");
                  return null;
              });
    }
    
    public void sendMessage(String text) {
        // Non-blocking send
        client.sendMessageAsync(text)
              .thenAccept(response -> {
                  chatTextArea.appendText("Server: " + response + "\n");
              });
    }
    
    public void setupListeners() {
        // Message listener
        client.addMessageListener(msg -> {
            chatTextArea.appendText("📨 " + msg + "\n");
        });
        
        // Connection listener
        client.addConnectionListener(
            () -> statusLabel.setText("🟢 Online"),
            () -> statusLabel.setText("🔴 Offline")
        );
        
        // Broadcast listener
        client.addBroadcastListener(msg -> {
            broadcastBox.getItems().add("📢 " + msg);
        });
    }
}
```

### Swing Example

```java
import javax.swing.*;
import com.tonyguerra.net.tcpmaster.core.TcpClient;

public class SwingChat {
    private TcpClient client;
    private JTextArea chatArea;
    
    public void setupClient() {
        client = new TcpClient("192.168.1.10", 9999);
        
        // Dispatch to Swing EDT
        client.setEventDispatcher(r -> SwingUtilities.invokeLater(r));
        
        // Async listeners
        client.addMessageListener(msg -> {
            chatArea.append("📨 " + msg + "\n");
        });
        
        client.connectAsync()
              .thenRun(() -> System.out.println("Connected"));
    }
}
```

---

## ⚙️ Threading Model

```
┌─────────────────────────┐
│     TcpServer           │
│  ┌─────────────────┐    │
│  │  AcceptThread   │ ← Main server thread
│  │ (listens 0.0.0) │    │
│  └────────┬────────┘    │
└───────────┼─────────────┘
            │
     ┌──────┴─────────────────────────┐
     ↓              ↓                 ↓
┌─────────┐   ┌─────────┐        ┌─────────┐
│ Client1 │   │ Client2 │  ···   │ ClientN │ ← CachedThreadPool
│ (Socket)│   │ (Socket)│        │ (Socket)│   (one thread/client)
└─────────┘   └─────────┘        └─────────┘
```

**Thread allocation:**
- 🖥️ **Server:** 1 background accept thread + 1 thread per client (pool)
- 💻 **Client:** 1 dedicated reader thread + async executor for callbacks
- 🔄 **Async APIs:** Internal `ExecutorService` (configurable)
- 🎨 **GUI dispatch:** Via `setEventDispatcher(...)` callback

**Thread safety:**
- ✅ ConcurrentHashMap for handler registry
- ✅ AtomicBoolean for connection state
- ✅ CopyOnWriteArrayList for listeners
- ✅ BlockingQueue for message serialization
- ✅ Synchronized blocks at critical points
---

## 📝 Logging

**This library uses SLF4J API only** — you choose the implementation.

### With Logback (Recommended)

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <scope>runtime</scope>
</dependency>
```

Create `logback.xml` in `src/main/resources`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="com.tonyguerra.net.tcpmaster" level="INFO"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### Other Implementations

- 📦 **Log4j2:** `org.apache.logging.log4j:log4j-slf4j-impl`
- ☕ **JUL:** `org.slf4j:slf4j-jdk14`
- 🚫 **None:** Library works without any implementation (SLF4J no-op)

---

## 📦 Java Module Support

Fully compatible with **Java 9+ module system**:

```java
module com.example.myapp {
    requires com.tonyguerra.net.tcpmaster;
    requires org.slf4j;  // SLF4J (optional)
}
```

Automatic module name: `com.tonyguerra.net.tcpmaster`

---

## 🌐 Network Usage

**Works in local networks!** Use any IP address (not just localhost).

```java
// Server listens on 0.0.0.0:9999 (all interfaces)
TcpServer server = new TcpServer(9999);
server.start();

// Client connects to specific machine on network
TcpClient client = new TcpClient("192.168.1.100", 9999);
client.connect();
```

👉 See [NETWORK_USAGE.md](NETWORK_USAGE.md) for complete guide on:
- Discovering IPs in your network
- Troubleshooting connection issues
- Performance optimization
- Security considerations
- Deployment checklist

---

## ❓ Frequently Asked Questions

### Q: Can I use this in production?
**A:** ✅ Yes! All 16 tests pass, zero compiler warnings, full thread-safety.

### Q: Does it work over internet?
**A:** ✅ Yes, but **NOT recommended** without TLS/SSL. Use only in trusted LAN environments.

### Q: How do I handle binary data?
**A:** ✅ Use `uploadFile()` / `uploadFileAsync()` for file transfer, or send custom binary protocols.

### Q: Can I run multiple servers?
**A:** ✅ Yes! Create separate instances: `new TcpServer(8000)`, `new TcpServer(8001)`, etc.

### Q: How do I scale to thousands of clients?
**A:** 🤔 tcp-master uses CachedThreadPool (one thread per client). For massive scale, consider Netty or Quarkus.

### Q: Is there TLS/SSL support?
**A:** ❌ Not yet. Currently works over plain TCP. Encryption support planned for v0.2.0.

### Q: How do I debug commands?
**A:** 🐛 Enable SLF4J DEBUG level or register handlers to log incoming messages.

---

## 🎨 Design Philosophy

| Principle | Why |
|-----------|-----|
| ⚙️ **No heavy frameworks** | Easier to embed, smaller footprint |
| 🧩 **Simple reflection DI** | Zero external dependencies, transparent |
| 📦 **Library-first** | Not a framework — you control flow |
| 🎯 **Explicit > magic** | Easy to understand, debug, and extend |
| 🧪 **Fully testable** | No hidden threads, deterministic behavior |
| 🧬 **Modern Java** | Java 21+, records, sealed classes |

---

## 📄 License

MIT License © Anthony Guerra

```
Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the "Software"),
to deal in the Software without restriction...
```

See [LICENSE.md](LICENSE.md) for details.

---

## 🙌 Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

**Made with ❤️ by [Anthony Guerra](https://github.com/tonyguerra122)**