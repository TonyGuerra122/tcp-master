package com.tonyguerra.net.tcpmaster.core.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class LineReaderTest {

    @Test
    void shouldReadSingleLineLf() throws Exception {
        final var in = new ByteArrayInputStream("hello\n".getBytes(StandardCharsets.UTF_8));
        final var lr = new LineReader(in);

        assertEquals("hello", lr.readLineUtf8());
        assertNull(lr.readLineUtf8());
    }

    @Test
    void shouldReadSingleLineCrlf() throws Exception {
        final var in = new ByteArrayInputStream("hello\r\n".getBytes(StandardCharsets.UTF_8));
        final var lr = new LineReader(in);

        assertEquals("hello", lr.readLineUtf8());
        assertNull(lr.readLineUtf8());
    }

    @Test
    void shouldReadMultipleLines() throws Exception {
        final var in = new ByteArrayInputStream("a\nb\nc\n".getBytes(StandardCharsets.UTF_8));
        final var lr = new LineReader(in);

        assertEquals("a", lr.readLineUtf8());
        assertEquals("b", lr.readLineUtf8());
        assertEquals("c", lr.readLineUtf8());
        assertNull(lr.readLineUtf8());
    }

    @Test
    void shouldReadEmptyLine() throws Exception {
        final var in = new ByteArrayInputStream("\n".getBytes(StandardCharsets.UTF_8));
        final var lr = new LineReader(in);

        assertEquals("", lr.readLineUtf8());
        assertNull(lr.readLineUtf8());
    }

    @Test
    void shouldReturnLastPartialLineOnEof() throws Exception {
        final var in = new ByteArrayInputStream("partial".getBytes(StandardCharsets.UTF_8));
        final var lr = new LineReader(in);

        assertEquals("partial", lr.readLineUtf8());
        assertNull(lr.readLineUtf8());
    }

    void shouldReturnNullOnImmediateEof() throws Exception {
        final var in = new ByteArrayInputStream(new byte[0]);
        final var lr = new LineReader(in);

        assertNull(lr.readLineUtf8());
    }
}
