package com.tonyguerra.net.tcpmaster.handlers;

import java.lang.reflect.Method;

import com.tonyguerra.net.tcpmaster.enums.TcpType;

public record HandlerDefinition(
        String command,
        TcpType type,
        Class<?> ownerClass,
        Method method) {
}
