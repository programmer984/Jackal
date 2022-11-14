package org.example.communication.logging;

@FunctionalInterface
public interface PostLogger {
    /**
     * after data was sent [and file was written]
     * return file id - for a reference in normal log4j text file
     * @param logId
     */
    void logAttempHappen(Integer logId);
}
