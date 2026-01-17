# tcp-master

A lightweight **TCP client/server library** for Java with **command-based handlers**, **annotation scanning**, and **simple dependency injection**.

Designed to be:
- Easy to embed in **CLI**, **GUI**, or **desktop applications**
- Extensible via annotations
- Safe to use as a **library** (no forced logging bindings)
- Friendly to **Java 21+** and modular projects

---

## Features

- ✅ TCP Server & Client
- ✅ Annotation-based command handlers
- ✅ Default commands + user-overridable commands
- ✅ Client-side and server-side command execution
- ✅ Broadcast support
- ✅ Simple DI container (no external framework)
- ✅ GUI-friendly async API
- ✅ Java Module support
- ✅ Maven Central ready

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.tonyguerra122</groupId>
    <artifactId>tcp-master</artifactId>
    <version>0.0.1</version>
</dependency>
```

---

## Basic Concepts
### Commands
Commands are plain text messages starting with `!`.
Examples:
```
!ping
!broadcast hello world
!disconnect
```
Each command is mapped to a handler method using annotations.

---

## Server Example
```java
package com.example.server;

import java.net.Socket;

import com.tonyguerra.net.tcpmaster.annotations.TcpHandler;
import com.tonyguerra.net.tcpmaster.enums.TcpType;

public final class ServerCommands {

    @TcpHandler(command = "!ping", type = TcpType.SERVER)
    public void ping(Socket client) {
        System.out.println("[SERVER] Ping from " + client.getRemoteSocketAddress());
    }
}
```
Supported server handler signatures:
```
()
(TcpServer)
(Socket)
(TcpServer, Socket)
(String)
(Socket, String)
(TcpServer, Socket, String)
```

---

## Client Example
### Create a Client
```java
TcpClient client = new TcpClient("127.0.0.1", 5000);
client.connect();

String response = client.sendMessage("hello");
System.out.println(response);

client.disconnect();
```

---

## Client Command Handler (Local)
```java
package com.example.client;

import com.tonyguerra.net.tcpmaster.annotations.TcpHandler;
import com.tonyguerra.net.tcpmaster.core.ClientCommandResult;
import com.tonyguerra.net.tcpmaster.enums.TcpType;

public final class ClientCommands {

    @TcpHandler(command = "!hello", type = TcpType.CLIENT)
    public ClientCommandResult hello() {
        System.out.println("Hello handled locally!");
        return ClientCommandResult.dontSend();
    }
}
```
Supported client handler signatures:
```
()
(TcpClient)
(TcpClient, String)
```

---

## Broadcast Example
```java
@TcpHandler(command = "!broadcast", type = TcpType.SERVER)
public void broadcast(TcpServer server, Socket client, String line) {
    String message = line.substring("!broadcast".length()).trim();
    server.broadcast(client, message);
}
```
Clients will receive:
```
[BROADCAST] /127.0.0.1:12345 -> hello
```

---

## Async & GUI-Friendly API
Designed to work well with **Swing**, **JavaFX**, or other UI frameworks.
```java
client.setEventDispatcher(r -> Platform.runLater(r));

client.connectAsync()
      .thenRun(() -> System.out.println("Connected"));

client.sendMessageAsync("hello")
      .thenAccept(System.out::println);
```

---

## Logging
This library depends only on SLF4J API.

You choose the logging implementation:

- Logback

- Log4j2

- JUL

- or none

Example (Logback, optional):
```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Java Modules
If you use **module-info.java**:
```java
module com.example.app {
    requires com.tonyguerra.net.tcpmaster;
}
```

The library provides an automatic module name:
```
com.tonyguerra.net.tcpmaster
```

---

## Design Philosophy
- ⚙️ No Spring, no Netty, no heavy frameworks

- 🧩 Simple reflection-based DI

- 📦 Library-first (not a framework)

- 🎯 Explicit behavior over magic

- 🧪 Fully testable

---

# License
MIT License © Anthony Guerra