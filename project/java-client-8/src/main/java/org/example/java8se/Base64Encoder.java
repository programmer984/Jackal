package org.example.java8se;

import org.example.Base64Tool;

import java.util.Base64;

public class Base64Encoder implements Base64Tool {
    @Override
    public String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public byte[] decode(String string) {
        return Base64.getDecoder().decode(string);
    }
}
