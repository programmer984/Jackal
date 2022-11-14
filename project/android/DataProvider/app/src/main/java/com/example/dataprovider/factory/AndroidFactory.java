package com.example.dataprovider.factory;

import android.content.Context;
import android.content.res.Resources;

import com.example.dataprovider.R;

import org.example.CommonConfig;
import org.example.softTimer.TimersManager;
import org.example.tools.*;
import org.example.udphole.sync.NodeTool;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

public class AndroidFactory implements UdpHoleDataPipeFactory {

    final TimersManager timersManager = new TimersManager();

    public AndroidFactory(Context context) throws IOException {
        Resources res =  context.getResources();
        InputStream inputStream = res.openRawResource(R.raw.app);
        CommonConfig.loadPropsAndClose(inputStream);
    }

    @Override
    public Base64Tool createBase64Tool() {
        return new Base64AndroidTool();
    }

    @Override
    public SynchronizationTool createSynchronizationTool() {
        return new NodeTool(createJsonTool(), createHttpTool());
    }

    @Override
    public RsaEncryptionTool createRsaEncryptionTool() {
        try {
            return new RsaEncryption(createBase64Tool());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AesEncryptionTool createAesEncryptionTool() {
        try {
            return new AesEncryption();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonTool createJsonTool() {
        return new Jackson();
    }

    @Override
    public HttpTool createHttpTool() {
        return new OkHttp();
    }

    @Override
    public TimersManager getTimersManager() {
        return timersManager;
    }

}
