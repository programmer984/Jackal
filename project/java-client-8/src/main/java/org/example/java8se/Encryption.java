package org.example.java8se;

import org.example.Base64Tool;
import org.example.Configuration;
import org.example.EncryptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

// Class to create an asymmetric key
public class Encryption implements EncryptionTool {

    private static final String CIPHER_ALG = "RSA/ECB/PKCS1Padding";
    private static final String RSA = "RSA";
    private static final int keySize = 2048;
    private static final Logger logger
            = LoggerFactory.getLogger("ConnectionLib");
    private static final String CIPHER_AES = "AES/CBC/PKCS5Padding";



    private Cipher cipherEncryptor;
    private Cipher cipherDecryptor;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private Base64Tool base64Tool;
    private SecureRandom random;
    private KeyGenerator keyGenerator;

    public Encryption(Base64Tool base64Tool) throws NoSuchPaddingException, NoSuchAlgorithmException {
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

    @Override
    public IvParameterSpec generateIv() {
        byte[] iv = new byte[Configuration.AES_SIZE];
        random.nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    @Override
    public SecretKeySpec generateAesKey() {
        keyGenerator.init(Configuration.AES_SIZE * 8);
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