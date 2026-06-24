package com.tonyguerra.net.tcpmaster.di;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class Container {
    private final Map<Class<?>, Object> singletons;
    private final Map<Class<?>, Supplier<?>> providers;
    private final ThreadLocal<Set<Class<?>>> resolvingStack = ThreadLocal.withInitial(HashSet::new);

    public Container() {
        singletons = new ConcurrentHashMap<>();
        providers = new ConcurrentHashMap<>();
    }

    /**
     * Registers a singleton instance in the container.
     * This instance will be reused for all subsequent requests for this type.
     *
     * @param <T>      the type to register
     * @param type     the class type (must not be null)
     * @param instance the instance to register (must not be null)
     * @throws NullPointerException if type or instance is null
     */
    public <T> void registerInstance(Class<T> type, T instance) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(instance);
        singletons.put(type, instance);
    }

    /**
     * Registers a provider (factory) for lazy instantiation of a type.
     * If the type is annotated with {@link Singleton}, the created instance will be
     * cached.
     * Otherwise, a new instance is created on each request.
     *
     * @param <T>      the type to register
     * @param type     the class type (must not be null)
     * @param provider the factory function to create instances (must not be null)
     * @throws NullPointerException if type or provider is null
     */
    public <T> void registerProvider(Class<T> type, Supplier<? extends T> provider) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(provider);
        providers.put(type, provider);
    }

    /**
     * Resolves and returns an instance of the specified type.
     * <p>
     * Resolution order:
     * <ol>
     * <li>Return cached singleton if available</li>
     * <li>Create via registered provider (and cache if @Singleton)</li>
     * <li>Create via constructor injection (scanning for @Inject annotated
     * constructor first)</li>
     * <li>Recursively resolve constructor parameters</li>
     * <li>Inject fields marked with @Inject</li>
     * </ol>
     * </p>
     * <p>
     * <strong>Circular Dependencies:</strong> If type A depends on B which depends
     * on A,
     * a {@link RuntimeException} will be thrown with a clear message.
     * </p>
     *
     * @param <T>  the type to resolve
     * @param type the class type (must not be null)
     * @return an instance of the requested type
     * @throws RuntimeException if circular dependency is detected or instantiation
     *                          fails
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Objects.requireNonNull(type);

        // Check singleton cache first (safe: we control what we put in)
        final var existing = singletons.get(type);
        if (existing != null) {
            // Type-safe cast: guaranteed by registerInstance contract
            return (T) existing;
        }

        // Check provider cache
        final var prov = providers.get(type);
        if (prov != null) {
            // Type-safe cast: Supplier is typed to produce T
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
        // Check for circular dependencies
        if (resolvingStack.get().contains(type)) {
            throw new RuntimeException(
                    String.format("Circular dependency detected while resolving: %s", type.getName()));
        }

        resolvingStack.get().add(type);
        try {
            final var ctor = pickConstructor(type);
            if (ctor == null) {
                throw new RuntimeException(
                        String.format("No suitable constructor found for %s", type.getName()));
            }

            final var args = new Object[ctor.getParameterCount()];
            final var paramTypes = ctor.getParameterTypes();

            for (int i = 0; i < paramTypes.length; i++) {
                args[i] = get(paramTypes[i]);
            }

            ctor.setAccessible(true);
            final var instance = (T) ctor.newInstance(args);

            injectFields(instance);
            return instance;
        } catch (RuntimeException ex) {
            throw ex; // Re-throw runtime exceptions as-is
        } catch (Exception ex) {
            throw new RuntimeException(
                    String.format("Failed to create instance of %s: %s",
                            type.getName(), ex.getMessage()),
                    ex);
        } finally {
            resolvingStack.get().remove(type);
            // Clean up ThreadLocal to prevent memory leaks in thread pools
            if (resolvingStack.get().isEmpty()) {
                resolvingStack.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Constructor<T> pickConstructor(Class<T> type) {
        final var ctors = type.getDeclaredConstructors();
        if (ctors.length == 0) {
            return null;
        }

        // Prefer @Inject annotated constructor
        for (final var c : ctors) {
            if (c.isAnnotationPresent(Inject.class)) {
                // Safe cast: we're picking from the class's own constructors
                return (Constructor<T>) c;
            }
        }

        // Try default no-arg constructor
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException ignore) {
            // Fall through to greedy approach
        }

        // Greedy: pick constructor with most parameters (best shot at auto-wiring)
        Constructor<?> best = ctors[0];
        for (final var c : ctors) {
            if (c.getParameterCount() > best.getParameterCount()) {
                best = c;
            }
        }

        // Safe cast: we control Constructor selection, best is from ctors
        return (Constructor<T>) best;
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
                    // Safe cast: field type is resolved and we get matching instance
                    final var dep = get((Class<?>) f.getType());
                    f.set(instance, dep);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(
                            String.format(
                                    "Cannot access field %s on %s (setAccessible failed)",
                                    f.getName(),
                                    instance.getClass().getName()),
                            ex);
                } catch (IllegalArgumentException ex) {
                    throw new RuntimeException(
                            String.format(
                                    "Type mismatch injecting field %s on %s (expected %s)",
                                    f.getName(),
                                    instance.getClass().getName(),
                                    f.getType().getName()),
                            ex);
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
