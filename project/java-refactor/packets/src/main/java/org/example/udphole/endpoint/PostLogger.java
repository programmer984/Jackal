package org.example.udphole.endpoint;

@FunctionalInterface
public interface PostLogger {
    void onFileWasWritten(int logId);
}
