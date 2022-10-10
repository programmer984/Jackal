package com.example.androidcontrol.factory;

import android.content.Context;
import android.content.res.Resources;


import com.example.androidcontrol.R;

import org.example.Base64Tool;
import org.example.BeansFactory;
import org.example.Configuration;
import org.example.EncryptionTool;
import org.example.HttpTool;
import org.example.JsonTool;
import org.example.java8se.Encryption;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

import javax.crypto.NoSuchPaddingException;

public class AndroidFactory implements BeansFactory {

    public AndroidFactory(Context context) throws IOException {
        Resources res =  context.getResources();
        InputStream inputStream = res.openRawResource(R.raw.app);
        Configuration.loadPropsAndClose(inputStream);
    }

    @Override
    public JsonTool getJsonTool() {
        return new Jackson();
    }

    @Override
    public HttpTool getHttpTool() {
        return new HttpClientTool();
    }

    @Override
    public Base64Tool getBase64Tool() {
        return new Base64AndroidTool();
    }

    @Override
    public String[] getStunServers() {
        return new String[] {"jitsi.org", "numb.viagenie.ca", "stun.ekiga.net"};
    }

    @Override
    public EncryptionTool createAsymmetric() {
        try {
            return new Encryption(getBase64Tool());
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }


}
