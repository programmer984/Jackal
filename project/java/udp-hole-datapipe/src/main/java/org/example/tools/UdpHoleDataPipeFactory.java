package org.example.tools;

import org.example.softTimer.TimersManager;

/**
 * if we use verb "get" - we return singleton
 * "create" - prototype
 */
public interface UdpHoleDataPipeFactory {
    Base64Tool createBase64Tool();
    SynchronizationTool createSynchronizationTool();
    RsaEncryptionTool createRsaEncryptionTool();
    AesEncryptionTool createAesEncryptionTool();
    JsonTool createJsonTool();
    HttpTool createHttpTool();
    TimersManager getTimersManager();
}
