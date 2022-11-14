package com.example.dataprovider.factory;

import android.util.Base64;

public class Base64AndroidTool implements org.example.tools.Base64Tool {
    @Override
    public String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    @Override
    public byte[] decode(String string) {
        return Base64.decode(string, Base64.NO_WRAP);
    }
}
