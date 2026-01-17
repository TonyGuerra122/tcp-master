package com.tonyguerra.net.tcpmaster.di;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class Container {
    private final Map<Class<?>, Object> singletons;
    private final Map<Class<?>, Supplier<?>> providers;

    public Container() {
        singletons = new ConcurrentHashMap<>();
        providers = new ConcurrentHashMap<>();
    }

    public <T> void registerInstance(Class<T> type, T instance) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(instance);
        singletons.put(type, instance);
    }

    public <T> void registerProvider(Class<T> type, Supplier<? extends T> provider) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(provider);
        providers.put(type, provider);
    }

    public <T> T get(Class<T> type) {
        Objects.requireNonNull(type);

        final var existing = singletons.get(type);
        if (existing != null) {
            return (T) existing;
        }

        final var prov = providers.get(type);
        if (prov != null) {
            final var obj = (T) prov.get();
            injectFields(obj);

            if (isSingleton(type)) {
                singletons.put(type, obj);
            }

            return obj;
        }

        final var created = create(type);

        if (isSingleton(type)) {
            singletons.put(type, created);
        }

        return created;
    }

    private boolean isSingleton(Class<?> type) {
        return type.isAnnotationPresent(Singleton.class);
    }

    private <T> T create(Class<T> type) {
        try {
            final var ctor = pickConstructor(type);

            final var args = new Object[ctor.getParameterCount()];
            final var paramTypes = ctor.getParameterTypes();

            for (int i = 0; i < paramTypes.length; i++) {
                args[i] = get(paramTypes[i]);
            }

            ctor.setAccessible(true);
            final var instance = ctor.newInstance(args);

            injectFields(instance);
            return instance;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create: " + type.getName(), ex);
        }
    }

    private <T> Constructor<T> pickConstructor(Class<T> type) {
        final var ctors = (Constructor<T>[]) type.getDeclaredConstructors();

        // prefer @Inject
        for (final var c : ctors) {
            if (c.isAnnotationPresent(Inject.class)) {
                return c;
            }
        }

        // else, if have a default
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException ignore) {
        }

        // else, get the maior (most deps)
        var best = ctors[0];
        for (final var c : ctors) {
            if (c.getParameterCount() > best.getParameterCount()) {
                best = c;
            }
        }

        return best;
    }

    private void injectFields(Object instance) {
        var t = instance.getClass();
        while (t != null && t != Object.class) {
            for (final var f : t.getDeclaredFields()) {
                if (!f.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                f.setAccessible(true);

                try {
                    final var dep = get(f.getType());
                    f.set(instance, dep);
                } catch (Exception ex) {
                    throw new RuntimeException(
                            String.format(
                                    "Failed to inject field %s on %s",
                                    f.getName(),
                                    instance.getClass().getName()),
                            ex);
                }
            }

            t = t.getSuperclass();
        }
    }
}
