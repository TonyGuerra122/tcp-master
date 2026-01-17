package com.tonyguerra.net.tcpmaster.standard;

import com.tonyguerra.net.tcpmaster.core.ClientCommandResult;
import com.tonyguerra.net.tcpmaster.enums.TcpType;
import com.tonyguerra.net.tcpmaster.handlers.TcpHandler;

public final class DefaultClientCommands {
    @TcpHandler(command = "!help", type = TcpType.CLIENT)
    public static ClientCommandResult help() {
        return ClientCommandResult.dontSend("""
                Commands:
                  !help        - show help
                  !version     - show version
                  /exit        - disconnect (UI/CLI command)
                """);
    }

    @TcpHandler(command = "!version", type = TcpType.CLIENT)
    public ClientCommandResult version() {
        return ClientCommandResult.dontSend("tcp-master v1.0.0");
    }
}
