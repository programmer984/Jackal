package org.example.udphole;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

public interface EncryptionTool {
    void applyKeys(PrivateKey privateKey, PublicKey publicKey) throws InvalidKeyException;

    // Encryption function which converts
    // the plainText into a cipherText
    // using private Key.
    byte[] do_RSAEncryption(byte[] input, int offset, int length) throws BadPaddingException, IllegalBlockSizeException;

    // Decryption function which converts
    // the ciphertext back to the
    // orginal plaintext.
    byte[] do_RSADecryption(byte[] input, int offset, int length) throws BadPaddingException, IllegalBlockSizeException;

    String keyToString(Key key);

    PublicKey getPublicKey(String publicKey) throws Exception;

    // Generating public and private keys
    // using RSA algorithm.
    KeyPair generateRSAKkeyPair()
            throws Exception;

    PublicKey getPublicKey(byte encoded[]) throws Exception;

    PrivateKey getPrivateKey(byte encoded[]) throws Exception;


    IvParameterSpec generateIv();

    SecretKeySpec generateAesKey();

    //Cipher.ENCRYPT_MODE
    Cipher createAESCipher(SecretKey key, IvParameterSpec iv, int modeEncryptDecrypt) throws InvalidAlgorithmParameterException,
            InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException;

}
