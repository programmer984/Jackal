package org.example;

@FunctionalInterface
public interface PostLogger {
    void onFileWasWritten(int logId);
}
