package com.tonyguerra.net.tcpmaster.configurations;

import java.nio.file.Path;

public final class Globals {
    private static String baseDirUploads = "uploads";

    public static Path getBaseDirUploads() {
        return Path.of(baseDirUploads).toAbsolutePath().normalize();
    }

    public static void setBaseDirUploads(String path) {
        baseDirUploads = path;
    }
}
