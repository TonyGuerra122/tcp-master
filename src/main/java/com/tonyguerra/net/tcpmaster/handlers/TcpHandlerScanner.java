package com.tonyguerra.net.tcpmaster.handlers;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.tonyguerra.net.tcpmaster.enums.TcpType;

public final class TcpHandlerScanner {

    private static final String DEFAULTS_PACKAGE = "com.tonyguerra.net.tcpmaster.standard";

    private TcpHandlerScanner() {
    }

    private static Reflections createReflections() {
        return new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .addScanners(Scanners.MethodsAnnotated));
    }

    public static Map<String, HandlerDefinition> scanDefaults(TcpType type) {
        return scan(type, ScanMode.DEFAULTS_ONLY);
    }

    public static Map<String, HandlerDefinition> scanUserHandlers(TcpType type) {
        return scan(type, ScanMode.USER_ONLY);
    }

    private enum ScanMode {
        DEFAULTS_ONLY,
        USER_ONLY
    }

    private static Map<String, HandlerDefinition> scan(TcpType type, ScanMode mode) {
        Map<String, HandlerDefinition> map = new ConcurrentHashMap<>();

        Reflections reflections = createReflections();

        for (Method method : reflections.getMethodsAnnotatedWith(TcpHandler.class)) {
            TcpHandler ann = method.getAnnotation(TcpHandler.class);
            if (ann.type() != type)
                continue;

            Class<?> owner = method.getDeclaringClass();
            String className = owner.getName();

            boolean isDefault = className.startsWith(DEFAULTS_PACKAGE);

            if (mode == ScanMode.DEFAULTS_ONLY && !isDefault)
                continue;
            if (mode == ScanMode.USER_ONLY && isDefault)
                continue;

            map.put(ann.command(), new HandlerDefinition(
                    ann.command(),
                    ann.type(),
                    owner,
                    method));
        }

        return Map.copyOf(map);
    }
}
