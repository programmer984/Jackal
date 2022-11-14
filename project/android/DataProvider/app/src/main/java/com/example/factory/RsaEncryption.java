package com.example.factory;

import org.example.tools.Base64Tool;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

// Class to create an asymmetric key
public class RsaEncryption implements org.example.tools.RsaEncryptionTool {

    private static final String CIPHER_ALG = "RSA/ECB/PKCS1Padding";
    private static final String RSA = "RSA";
    private static final int keySize = 2048;



    private Cipher cipherEncryptor;
    private Cipher cipherDecryptor;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private Base64Tool base64Tool;
    private SecureRandom random;
    private KeyGenerator keyGenerator;

    public RsaEncryption(Base64Tool base64Tool) throws GeneralSecurityException {
        this.base64Tool = base64Tool;
        cipherEncryptor = Cipher.getInstance(CIPHER_ALG);
        cipherDecryptor = Cipher.getInstance(CIPHER_ALG);
        random = SecureRandom.getInstance("SHA1PRNG");
        keyGenerator = KeyGenerator.getInstance("AES");
    }

    @Override
    public void applyKeys(PrivateKey privateKey, PublicKey publicKey) throws InvalidKeyException {
        this.publicKey = publicKey;
        this.privateKey = privateKey;

        cipherEncryptor.init(Cipher.ENCRYPT_MODE, publicKey);
        cipherDecryptor.init(Cipher.DECRYPT_MODE, privateKey);
    }


    // Encryption function which converts
    // the plainText into a cipherText
    // using private Key.
    @Override
    public byte[] do_RSAEncryption(byte[] input, int offset, int length) throws BadPaddingException, IllegalBlockSizeException {
        return cipherEncryptor.doFinal(input, offset, length);
    }

    // Decryption function which converts
    // the ciphertext back to the
    // orginal plaintext.
    @Override
    public byte[] do_RSADecryption(byte[] input, int offset, int length) throws BadPaddingException, IllegalBlockSizeException {

        return cipherDecryptor.doFinal(input, offset, length);
    }


    /**
     * Convert key to string.
     *
     * @return String representation of key
     */
    @Override
    public String keyToString(Key key) {

        /* Get key in encoding format */
        byte encoded[] = key.getEncoded();
        String encodedKey = base64Tool.encode(encoded);
        return encodedKey;
    }


    @Override
    public PublicKey getPublicKey(String publicKey) throws Exception {
        byte[] key = base64Tool.decode(publicKey);
        return getPublicKey(key);
    }


    // Generating public and private keys
    // using RSA algorithm.
    @Override
    public KeyPair generateRSAKkeyPair()
            throws Exception {
        KeyPairGenerator keyPairGenerator
                = KeyPairGenerator.getInstance(RSA);

        keyPairGenerator.initialize(keySize, random);

        return keyPairGenerator
                .generateKeyPair();
    }


    @Override
    public PublicKey getPublicKey(byte encoded[]) throws Exception {
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);
        return pubKey;
    }

    @Override
    public PrivateKey getPrivateKey(byte encoded[]) throws Exception {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        PrivateKey priKey = keyFactory.generatePrivate(keySpec);
        return priKey;
    }



}