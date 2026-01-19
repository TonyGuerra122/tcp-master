package com.tonyguerra.net.tcpmaster.core.components;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class LineReader {
    private final InputStream in;

    public LineReader(InputStream in) {
        this.in = in;
    }

    /**
     * Reads a UTF-8 line terminated by '\n'
     * '\r' characters are ignored
     * 
     * @return the line without line-breaks, or null if stream is closed
     */
    public String readLineUtf8() throws IOException {
        final var buffer = new ByteArrayOutputStream(128);

        while (true) {
            final int b = in.read();

            if (b == -1) {
                // Stream closed
                return buffer.size() == 0
                        ? null
                        : buffer.toString(StandardCharsets.UTF_8);
            }

            if (b == '\n') {
                break;
            }

            if (b == '\r') {
                continue;
            }

            buffer.write(b);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }
}
