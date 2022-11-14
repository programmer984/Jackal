package org.example.tools;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

public interface AesEncryptionTool {

    IvParameterSpec generateIv();

    SecretKeySpec generateAesKey();

    //Cipher.ENCRYPT_MODE
    Cipher createAESCipher(SecretKey key, IvParameterSpec iv, int modeEncryptDecrypt) throws GeneralSecurityException;

}
