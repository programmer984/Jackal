package org.example;

import static org.junit.Assert.assertTrue;

import org.example.serviceComponents.imageCreating.BitmapParts;
import org.example.serviceComponents.imageCreating.ImagePart;
import org.example.serviceComponents.imageCreating.ImagePartsConfiguration;
import org.example.serviceComponents.imageCreating.ImageProcessor;
import org.example.serviceComponents.imageParsing.ImageCell;
import org.example.serviceComponents.packets.*;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Unit test for simple App.
 */
public class AppTest {
    int width = 1440;
    int height = 1080;
    ImageProcessor imageProcessor = new ImageProcessor();

    @Test
    public void testImagePartStream() {

        byte[] bitmap = new byte[100 * 100];
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 100; j++) {
                bitmap[(i * 100) + j] = (byte) j;
            }
        }

        ImagePartsConfiguration configuration = new ImagePartsConfiguration(0, 0, 0, 10, 10, 100);
        ImagePart imagePart = new ImagePart(1, 1, bitmap, configuration);
        List<Byte> list = imagePart.getByteStream().collect(Collectors.toList());
        assertTrue(list.size() == 100);
        assertTrue(list.get(11) == 11);
        Iterator<Byte> iterator = imagePart.iterator();
        int offset = 0;
        while (iterator.hasNext()) {
            byte next = iterator.next();
            if (offset++ == 12) {
                assertTrue(next == 12);
            }
        }
    }


    @Test
    public void videoFrameSplitTest() {
        byte[] buf = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        VideoFramePacket[] parts = VideoFramePacket.split(buf, 2, 4);
        byte[] lastPacket = parts[2].toArray();
        assertTrue(VideoFramePacket.getVideFrameSize(lastPacket, 0) == 2);
        int bodyOffset = VideoFramePacket.getDataOffset(0);
        assertTrue(lastPacket[bodyOffset + 1] == 9);
    }

    @Test
    public void videoFrameSplitTest2() {
        byte[] buf = new byte[100];
        int mtu = 70;
        Arrays.fill(buf, mtu, buf.length, (byte) 1);
        VideoFramePacket[] parts = VideoFramePacket.split(buf, 2, mtu);
        assertTrue(parts.length == 2);
    }


    @Test
    public void semicolonTest() {
        int size = 14;
        size = AbstractPacket.semicolon(size);
        assertTrue(size == 16);
    }

    @Test
    public void drawCanvas() throws IOException {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_BYTE_GRAY); //BufferedImage.TYPE_3BYTE_BGR);
        int rgb = Color.GRAY.getRGB();
        for (int x = 20; x < 30; x++) {
            for (int y = 20; y < 30; y++) {
                image.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(image, "jpg", new File("./target/50.jpg"));
    }


    @Test
    public void splitCar() throws IOException, URISyntaxException, InterruptedException {

        byte[] fileRawData = readTestImage();
        imageProcessor.onNv21Frame(fileRawData, width, height);
        Thread.sleep(50);
        imageProcessor.onNv21Frame(fileRawData, width, height);
        Thread.sleep(100);

        assertTrue(fileRawData != null);
        Thread.sleep(100);

        BitmapParts parts = imageProcessor.getBitmapParts();
        ImagePartsConfiguration configuration = parts.getConfiguration();

        Files.createDirectories(Paths.get("./target", "car-picture"));

        for (ImagePart part : parts.getParts()) {
            BufferedImage bufferedImage = new BufferedImage(configuration.getWidth(), configuration.getHeight(),
                    BufferedImage.TYPE_BYTE_GRAY);
            Iterator<Byte> iterator = part.iterator();
            for (int i = 0; i < configuration.getHeight(); i++) {
                for (int j = 0; j < configuration.getWidth(); j++) {
                    int b = iterator.next() & 0xFF;
                    b = 0xFF000000 | (b << 16 | b << 8 | b);
                    bufferedImage.setRGB(j, i, b);
                }
            }

            ImageIO.write(bufferedImage, "jpg", new File(String.format("./target/car-picture/%d-%d.jpg",
                    part.getRow(), part.getCol())));
        }

        imageProcessor.join();
    }


    @Test
    public void ImagePacketTest() throws IOException, URISyntaxException, InterruptedException {

        byte[] fileRawData = readTestImage();
        imageProcessor.onNv21Frame(fileRawData, width, height);
        Thread.sleep(500);
        BitmapParts parts = imageProcessor.getBitmapParts();
        ImagePart part = parts.getParts().get(Utils.random(0, parts.getParts().size() - 1));
        ImagePartsConfiguration configuration = parts.getConfiguration();

        ImagePartPacket packet = new ImagePartPacket(part, configuration);
        byte[] buf = packet.toArray();

        ImageCell imageCell = ImagePartPacket.fromPacket(buf, 0);

        Iterator<Byte> iterator = part.iterator();
        Iterator<Byte> iterator2 = imageCell.iterator();

        boolean equals = true;
        for (int i = 0; i < configuration.getHeight(); i++) {
            for (int j = 0; j < configuration.getWidth(); j++) {
                byte b = iterator.next();
                byte b2 = iterator2.next();
                if (b != b2) {
                    equals = false;
                    break;
                }
            }
        }
        assertTrue(equals);
    }

    private byte[] readTestImage() throws IOException, URISyntaxException {
        byte[] result = null;
        URL filePath = this.getClass().getClassLoader().getResource("file.jpg");
        File file = Paths.get(filePath.toURI()).toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            int size = inputStream.available();
            result = new byte[size];
            inputStream.read(result);
        }
        return result;
    }

    @Test
    public void LacksPacketTest(){
        Map<Integer, Set<Integer>> lacks = new HashMap<>();
        Set<Integer> id_1 = new HashSet<>();
        id_1.add(3);id_1.add(5);id_1.add(1);
        Set<Integer> id_2 = new HashSet<>();
        id_2.add(23);id_2.add(15);id_2.add(14);

        lacks.put(3344423, id_1);
        lacks.put(545454, id_2);

        byte[] buf = new LacksRequest(lacks).toArray();
        Map<Integer, Set<Integer>> lacks2 = LacksRequest.getLacks(buf, 0);
        assertTrue(lacks2.get(545454).contains(15));
    }

}
