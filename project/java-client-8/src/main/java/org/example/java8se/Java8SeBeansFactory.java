package org.example.java8se;

import org.example.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class Java8SeBeansFactory implements BeansFactory {

    private static final Logger logger
            = LoggerFactory.getLogger("ConnectionLib");

    public Java8SeBeansFactory(){
        try {
            Configuration.loadPropsAndClose();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonTool getJsonTool() {
        return new Jackson();
    }

    @Override
    public HttpTool getHttpTool() {
        return new ApacheHttpClient();
    }


    @Override
    public Base64Tool getBase64Tool() {
        return new Base64Encoder();
    }

    @Override
    public String[] getStunServers() {
        return new String[]{"jitsi.org", "numb.viagenie.ca", "stun.ekiga.net"};
    }

    @Override
    public EncryptionTool createAsymmetric() {
        try {
            return new Encryption(getBase64Tool());
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            logger.error("Asymmetric", e);
        }
        return null;
    }
}
