package org.example.tools;

public interface JsonTool {
    String generateString(Object obj) throws JsonConvertException;
    <T> T generateObject(String json, Class<T> klass)  throws JsonConvertException;
}
