package com.tonyguerra.net.tcpmaster.core.components;

@FunctionalInterface
public interface ProgressCallback {
    /**
     * @param sentBytes  bytes already sent
     * @param totalBytes total bytes to send
     */
    void onProgress(long sentBytes, long totalBytes);
}
