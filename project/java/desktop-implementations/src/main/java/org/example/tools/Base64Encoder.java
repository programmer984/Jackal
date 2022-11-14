package org.example.tools;


import java.util.Base64;

public class Base64Encoder implements org.example.tools.Base64Tool {
    @Override
    public String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public byte[] decode(String string) {
        return Base64.getDecoder().decode(string);
    }
}
