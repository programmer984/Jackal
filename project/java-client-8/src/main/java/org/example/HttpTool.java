package org.example;

import java.util.Map;
import java.util.function.Consumer;

public interface HttpTool {
    void post(String url, Map<String, String> headers, String content) throws HttpException;
    String get(String url, Map<String, String> headers) throws HttpException;
}
