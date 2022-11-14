package org.example.encryption;

import org.example.TimeUtils;
import org.example.tools.AesEncryptionTool;
import org.example.tools.RsaEncryptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class AesConfig implements Cloneable {
    private static final Logger logger
            = LoggerFactory.getLogger("ConnectionLib");

    private AesEncryptionTool encryptionTool;
    private IvParameterSpec iv;
    private SecretKeySpec key;
    private Cipher cipher;
    private int mode;
    private int creationTimestamp;

    private AesConfig() {
    }

    public AesConfig(AesEncryptionTool encryption)  {
        encryptionTool = encryption;
        iv = encryption.generateIv();
        key = encryption.generateAesKey();
        mode = Cipher.ENCRYPT_MODE;
        creationTimestamp = (int) TimeUtils.nowMs();
        try {
            cipher = encryption.createAESCipher(key, iv, mode);
        } catch (GeneralSecurityException e) {
            logger.error("aes init", e);
        }
    }

    public AesConfig(AesEncryptionTool encryption, SecretKeySpec key, IvParameterSpec iv) {
        encryptionTool = encryption;
        this.iv = iv;
        this.key = key;
        mode = Cipher.DECRYPT_MODE;
        creationTimestamp = (int) TimeUtils.nowMs();
        try {
            cipher = encryption.createAESCipher(key, iv, mode);
        } catch (GeneralSecurityException e) {
            logger.error("aes init", e);
        }
    }


    public IvParameterSpec getIv() {
        return iv;
    }

    public SecretKeySpec getKey() {
        return key;
    }

    public Cipher getCipher() {
        return cipher;
    }

    @Override
    public AesConfig clone() {
        AesConfig aesConfig = new AesConfig();
        try {
            aesConfig.cipher = encryptionTool.createAESCipher(key, iv, mode);
            aesConfig.key = key;
            aesConfig.iv = iv;
            aesConfig.creationTimestamp = creationTimestamp;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return aesConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AesConfig aesConfig = (AesConfig) o;
        return creationTimestamp == aesConfig.creationTimestamp;
    }

    @Override
    public int hashCode() {
        return creationTimestamp;
    }
}
