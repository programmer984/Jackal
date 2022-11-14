package org.example.tools;

import java.util.Map;

public interface HttpTool {
    void post(String url, Map<String, String> headers, String content) throws HttpException;
    String get(String url, Map<String, String> headers) throws HttpException;
}
