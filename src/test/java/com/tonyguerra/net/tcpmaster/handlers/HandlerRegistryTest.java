package com.tonyguerra.net.tcpmaster.handlers;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tonyguerra.net.tcpmaster.enums.TcpType;

final class HandlerRegistryTest {
    static final class Dummy {
        public void a() {
        }

        public void b() {
        }
    }

    static HandlerDefinition def(String cmd, String methodName) throws Exception {
        final var m = Dummy.class.getMethod(methodName);

        return new HandlerDefinition(cmd, TcpType.SERVER, Dummy.class, m);
    }

    @Test
    static void userShouldOverrideDefault() throws Exception {
        final var r = new HandlerRegistry();

        final var defDefault = def("!help", "a");
        final var defUser = def("!help", "b");

        r.registerDefault(Map.of("!help", defDefault));
        r.registerUser(Map.of("!help", defUser));

        assertSame(defUser, r.resolve("!help"));
    }

    @Test
    static void shouldReturnDefaultWhenNoUserHandler() throws Exception {
        final var r = new HandlerRegistry();

        final var defDefault = def("!ping", "a");
        r.registerDefault(Map.of("!ping", defDefault));

        assertSame(defDefault, r.resolve("!ping"));
        assertNull(r.resolve("!missing"));
    }

    @Test
    static void mergedViewShouldContainUserVersion() throws Exception {
        final var r = new HandlerRegistry();

        final var defDefault = def("!help", "a");
        final var defUser = def("!help", "b");
        final var defOther = def("!ping", "a");

        r.registerDefault(Map.of("!help", defDefault, "!ping", defOther));
        r.registerUser(Map.of("!help", defUser));

        final var merged = r.mergedView();
        assertSame(defUser, merged.get("!help"));
        assertSame(defOther, merged.get("!ping"));
    }
}
