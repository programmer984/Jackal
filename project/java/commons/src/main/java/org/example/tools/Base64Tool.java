package org.example.tools;

public interface Base64Tool {
    String encode(byte[] bytes);
    byte[] decode(String string);
}
