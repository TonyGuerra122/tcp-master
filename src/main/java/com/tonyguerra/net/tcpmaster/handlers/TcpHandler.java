package com.tonyguerra.net.tcpmaster.handlers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.tonyguerra.net.tcpmaster.enums.TcpType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TcpHandler {
    String command();

    TcpType type();
}
