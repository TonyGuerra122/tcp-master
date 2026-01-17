package com.tonyguerra.net.tcpmaster.handlers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HandlerRegistry {
    private final Map<String, HandlerDefinition> defaults;
    private final Map<String, HandlerDefinition> user;

    public HandlerRegistry() {
        defaults = new ConcurrentHashMap<>();
        user = new ConcurrentHashMap<>();
    }

    public void registerDefault(Map<String, HandlerDefinition> map) {
        defaults.putAll(map);
    }

    public void registerUser(Map<String, HandlerDefinition> map) {
        user.putAll(map);
    }

    /** User overrides default if command clashes. */
    public HandlerDefinition resolve(String command) {
        final var u = user.get(command);
        if (u != null) {
            return u;
        }

        return defaults.get(command);
    }

    /** Merged view (user wins) */
    public Map<String, HandlerDefinition> mergedView() {
        final Map<String, HandlerDefinition> merged = new ConcurrentHashMap<>();
        merged.putAll(user);

        return Map.copyOf(merged);
    }
}
