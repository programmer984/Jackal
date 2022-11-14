package org.example.udphole;

import java.io.*;
import java.util.Hashtable;
import java.util.Properties;

public class Configuration {
    public static final int AES_SIZE = 16;
    public static final int RSA_MinimumSize = 256;
    public static final String AES = "AES";
    private static final String propName = "properties";

    private static final Properties appProps = new Properties();
    public static boolean logPackets;
    public static String packetsDir;
    public static String videoSink;

    public static void loadPropsAndClose() throws IOException {
        Properties properties = System.getProperties();
        if (properties.containsKey(propName)) {
            File propertiesFile = new File(properties.getProperty(propName));
            if (propertiesFile.exists() && propertiesFile.isFile()) {
                loadPropsAndClose(new FileInputStream(propertiesFile));
            }
        }

        InputStream inputStream = Configuration.class.getClassLoader()
                .getResourceAsStream("app.properties");
        loadPropsAndClose(inputStream);
    }

    /**
     * Configuration.class.getClassLoader().getResourceAsStream("app.properties")
     * context.getResources()...
     *
     * @param inputStream
     */
    public static void loadPropsAndClose(InputStream inputStream) throws IOException {
        try {
            appProps.load(inputStream);
            logPackets = Boolean.parseBoolean(getOrDefault(appProps, "log_packets", "false"));
            packetsDir = getOrDefault(appProps, "packets_folder", "./packets").toString();
            videoSink = getOrDefault(appProps, "video_sink", "vlc :rtsp-caching=150 - --demux=h264").toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            inputStream.close();
        }
    }

    public static <T, M extends Hashtable<Object, Object>> T getOrDefault(M map, String key, T defaultValue) {
        T value = (T) map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }


}
