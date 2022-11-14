package com.example.dataprovider.factory;

import org.example.CommonConfig;
import org.example.tools.AesEncryptionTool;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;


public class AesEncryption implements AesEncryptionTool {

    private static final String CIPHER_AES = "AES/CBC/PKCS5Padding";

    private SecureRandom random;
    private KeyGenerator keyGenerator;

    public AesEncryption() throws GeneralSecurityException {
        random = SecureRandom.getInstance("SHA1PRNG");
        keyGenerator = KeyGenerator.getInstance("AES");
    }




    @Override
    public IvParameterSpec generateIv() {
        byte[] iv = new byte[CommonConfig.AES_SIZE];
        random.nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    @Override
    public SecretKeySpec generateAesKey() {
        keyGenerator.init(CommonConfig.AES_SIZE * 8);
        return (SecretKeySpec) keyGenerator.generateKey();
    }

    //Cipher.ENCRYPT_MODE
    @Override
    public Cipher createAESCipher(SecretKey key, IvParameterSpec iv, int modeEncryptDecrypt) throws InvalidAlgorithmParameterException,
            InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance(CIPHER_AES);
        cipher.init(modeEncryptDecrypt, key, iv);
        return cipher;
    }


}