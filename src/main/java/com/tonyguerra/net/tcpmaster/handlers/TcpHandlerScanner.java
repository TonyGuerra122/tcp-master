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
    private static volatile Reflections cachedReflections;

    private TcpHandlerScanner() {
    }

    /**
     * Creates or returns cached Reflections instance.
     * Scans only the tcp-master package (not entire classpath) to improve
     * performance.
     * Uses double-checked locking for lazy initialization.
     */
    private static Reflections createReflections() {
        if (cachedReflections == null) {
            synchronized (TcpHandlerScanner.class) {
                if (cachedReflections == null) {
                    cachedReflections = new Reflections(new ConfigurationBuilder()
                            .setUrls(ClasspathHelper.forPackage("com.tonyguerra.net.tcpmaster"))
                            .addScanners(Scanners.MethodsAnnotated));
                }
            }
        }
        return cachedReflections;
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
