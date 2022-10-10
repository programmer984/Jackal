package org.example;

import org.example.java8se.Java8SeBeansFactory;
import org.example.stun.StunException;
import org.junit.Ignore;
import org.junit.Test;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Unit test for simple App.
 */
public class LibTest {

    EncryptionTool asymmetric;
    BeansFactory factory;

    public LibTest() {

        FactoryHolder.setFactory(new Java8SeBeansFactory());
        factory = FactoryHolder.getFactory();
        asymmetric = FactoryHolder.getFactory().createAsymmetric();
    }

    @Ignore
    @Test
    public void removeOldRowsTest() throws HttpException {
        Back4AppTool syncTool = new Back4AppTool();

        syncTool.removeOldRecords(factory.getHttpTool());

    }


    @Test
    public void clientPublishAndGet() {
        Runnable client1 = () -> {
            try {
                client("java1", "java2");
            } catch (Exception e) {
                e.printStackTrace();
                fail();
            }
        };

        Runnable client2 = () -> {
            try {
                client("java2", "java1");
            } catch (Exception e) {
                e.printStackTrace();
                fail();
            }
        };

        Stream.of(client1, client2).parallel().forEach(Runnable::run);
    }

    private void client(String thisName, String thatName) throws InterruptedException, SocketException,
            JsonConvertException, HttpException, StunException {
        BeansFactory factory = FactoryHolder.getFactory();
        ClientPublisher cp = new ClientPublisher(thisName);

        ClientInfo thisInfo = cp.retrievePublicAddress("jitsi.org", "numb.viagenie.ca", "stun.ekiga.net");
        ClientInfo otherInfo = null;
        assert (thisInfo != null);
        if (cp.sendInfo(thisInfo)) {
            while (true) {
                otherInfo = cp.retrieveInfoAbout(thatName);
                if (otherInfo != null) {
                    break;
                }
                Thread.sleep(100);
            }
            UDPHolePuncher c = new UDPHolePuncher(thisInfo, otherInfo);
            if (c.connect(10)) {
                List<UDPEndPoint> endPoints = c.getRemoteEndpoints();
                for (UDPEndPoint ep : endPoints) {
                    System.out.println(ep.toString());
                }
                assertTrue(endPoints.size() > 0);
            } else {
                fail();
            }
            if (thisInfo.getSocket().isConnected()) {
                thisInfo.getSocket().close();
            }
        }

    }


    //@Test
    public void GetAvailableChipers() {
        Set<String> algs = new TreeSet<>();
        for (Provider provider : Security.getProviders()) {
            provider.getServices().stream()
                    .filter(s -> "Cipher".equals(s.getType()))
                    .map(Provider.Service::getAlgorithm)
                    .forEach(algs::add);
        }
        algs.forEach(System.out::println);
    }

    @Test
    public void localIpFiltering() {
        String remotes = "172.27.0.1,192.168.1.10,192.168.1.19";
        String locals = "172.47.0.1,192.168.1.4";
        List<InetAddress> addresses = ClientInfo.getIntersectedLocalIp(remotes, locals);
        assert (addresses.size() == 2);
    }


    @Test
    public void connectionPacketTest() throws UnknownHostException {
        byte[] testFingerPrint = new byte[]{4, 65, 32, 7, 0, 3};
        ConnectionPacket cp = new ConnectionPacket(true, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}),
                64000, testFingerPrint);
        byte[] buf = cp.createPacket();
        assertTrue(ConnectionPacket.isValidCrc(buf));
        assertFalse(ConnectionPacket.isEcho(buf));
        assertTrue(ConnectionPacket.fingerPrintEquals(buf, testFingerPrint));
    }


    @Test
    public void encryptTest() throws Exception {
        KeyPair keyPair = asymmetric.generateRSAKkeyPair();
        byte[] test = new byte[]{1, 2, 3, 4, 5, 6, 7};
        asymmetric.applyKeys(keyPair.getPrivate(), keyPair.getPublic());
        byte[] encrypted = asymmetric.do_RSAEncryption(test, 0, test.length);
        byte[] test2 = asymmetric.do_RSADecryption(encrypted, 0, encrypted.length);
        assertTrue(test[0] == test2[0]);

    }


    @Test
    public void encryptTest2() throws Exception {
        byte[] test = new byte[]{1, 2, 3, 4, 5, 6, 7};
        KeyPair keyPair1 = asymmetric.generateRSAKkeyPair();
        String publicKey = asymmetric.keyToString(keyPair1.getPublic());
        System.out.println(publicKey);
        PublicKey publicKey1 = asymmetric.getPublicKey(publicKey);

        asymmetric.applyKeys(keyPair1.getPrivate(), publicKey1);
        byte[] encrypted = asymmetric.do_RSAEncryption(test, 0, test.length);
        byte[] test2 = asymmetric.do_RSADecryption(encrypted, 0, encrypted.length);
        assertTrue(test[0] == test2[0]);
    }


    @Test
    public void AesTest() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        IvParameterSpec iv = asymmetric.generateIv();
        SecretKey s = asymmetric.generateAesKey();
        Cipher encryptor = asymmetric.createAESCipher(s, iv, Cipher.ENCRYPT_MODE);

        byte[] buf = new byte[16];
        buf[3] = 0x33;
        byte[] e = encryptor.doFinal(buf, 0, 16);

        SecretKeySpec fromBytesKey = new SecretKeySpec(s.getEncoded(), Configuration.AES);
        IvParameterSpec ivfromBytes = new IvParameterSpec(iv.getIV());
        Cipher decryptor = asymmetric.createAESCipher(fromBytesKey, ivfromBytes, Cipher.DECRYPT_MODE);
        byte[] decrypted = decryptor.doFinal(e);
        assertTrue(buf[3] == decrypted[3]);
    }

    @Test
    public void AesEchoPacketTest() {
        IvParameterSpec iv1 = asymmetric.generateIv();
        SecretKeySpec s1 = asymmetric.generateAesKey();
        AesConfig config1 = new AesConfig(asymmetric, s1, iv1);

        IvParameterSpec iv2 = asymmetric.generateIv();
        SecretKeySpec s2 = asymmetric.generateAesKey();
        AesConfig config2 = new AesConfig(asymmetric, s2, iv2);

        AesConfigurationPacket aes = new AesConfigurationPacket(s1, iv1);
        byte[] buf = aes.createPacket();
        assertTrue(AesConfigurationPacket.crcCorrect(buf));

        AesConfigurationPacket.setEchoReply(buf, config2);


        assertTrue(AesConfigurationPacket.isEcho(buf));
        assertTrue(AesConfigurationPacket.crcCorrect(buf));

        assertTrue(AesConfigurationPacket.containsKey(buf, s1.getEncoded()));
    }


}
