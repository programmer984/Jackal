package org.example;

import org.example.java8se.Java8SeBeansFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.Assert.assertTrue;

/**
 * Unit test for simple App.
 */
public class EncryptTest {

    BeansFactory factory;
    EncryptionTool tool;
    AesConfig aesEncryptCommon;
    AesConfig aesDecryptCommon;

    ThreadLocal<AesConfig> encrypt = new ThreadLocal<>();
    ThreadLocal<AesConfig> decrypt = new ThreadLocal<>();


    public EncryptTest() {
        FactoryHolder.setFactory(new Java8SeBeansFactory());
        factory = FactoryHolder.getFactory();
        tool = factory.createAsymmetric();
        aesEncryptCommon = new AesConfig(tool);
        aesDecryptCommon = new AesConfig(tool, aesEncryptCommon.getKey(), aesEncryptCommon.getIv());
    }

    Supplier<AesConfig> encSupplier = new Supplier<AesConfig>() {
        @Override
        public AesConfig get() {
            if (encrypt.get() == null) {
                encrypt.set(aesEncryptCommon.clone());
            }
            return encrypt.get();
        }
    };

    Supplier<AesConfig> decSupplier = new Supplier<AesConfig>() {
        @Override
        public AesConfig get() {
            if (decrypt.get() == null) {
                decrypt.set(aesDecryptCommon.clone());
            }
            return decrypt.get();
        }
    };

    @Test
    public void encryptTest() {


        IntStream.range(0, 255).parallel()
                .forEach(i -> {
                    try {
                        byte[] testBuf = new byte[Configuration.AES_SIZE * 100];
                        Arrays.fill(testBuf, (byte) i);
                        AesConfig aesEncrypt = encSupplier.get();
                        byte[] enc = aesEncrypt.getCipher().doFinal(testBuf);
                        AesConfig aesDecrypt = decSupplier.get();
                        enc = aesDecrypt.getCipher().doFinal(enc);
                        assertTrue((enc[0] & 0xFF) == i);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }


}
