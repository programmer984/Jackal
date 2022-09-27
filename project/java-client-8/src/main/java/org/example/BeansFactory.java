package org.example;

public interface BeansFactory {

    JsonTool getJsonTool();

    HttpTool getHttpTool();

    Base64Tool getBase64Tool();

    String[] getStunServers();

    EncryptionTool createAsymmetric();
}
