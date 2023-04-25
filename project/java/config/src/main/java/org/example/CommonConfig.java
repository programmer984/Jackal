package org.example;

import java.io.*;
import java.util.*;

public class CommonConfig {
    public static final int PACKET_SIZE_MAX = 1472;
    public static final int PACKET_SIZE_PREFERRED = 1360;
    public static final int AES_SIZE = 16;
    public static final int RSA_MinimumSize = 256;
    public static final String AES = "AES";
    private static final String propName = "properties";
    public static String CHANNEL_TYPE_PLAIN_UDP = "plain_udp";
    public static String CHANNEL_TYPE_HOLE_UDP = "hole_udp";

    private static final Properties appProps = new Properties();
    public static boolean logPackets;
    public static boolean logCodecPackets;
    public static boolean recordVideo;
    public static String packetsDir;
    public static String videoSink;
    public static String stunServers;
    public static String nodeSyncServer;
    public static String udpHoleThisName, udpHoleThatName;
    public static Integer plainUdpLocalPort;
    public static String plainUdpRemoteAddress;
    public static String mainChannel; //plain_udp, hole_udp

    public static List<String> getSettingAsList(String value){
        if (value==null || value.isEmpty()){
            return Collections.emptyList();
        }
        return Arrays.asList(value.split("[ ,]+"));
    }

    public static void loadPropsAndClose() throws IOException {
        Properties properties = System.getProperties();
        if (properties.containsKey(propName)) {
            File propertiesFile = new File(properties.getProperty(propName));
            if (propertiesFile.exists() && propertiesFile.isFile()) {
                loadPropsAndClose(new FileInputStream(propertiesFile));
                return;
            }
        }

        //load defaults from resources
        InputStream inputStream = CommonConfig.class.getClassLoader()
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
            logCodecPackets = Boolean.parseBoolean(getOrDefault(appProps, "log_codec_packets", "false"));
            recordVideo = Boolean.parseBoolean(getOrDefault(appProps, "record_video", "false"));
            packetsDir = getOrDefault(appProps, "packets_folder", "./packets");
            videoSink = getOrDefault(appProps, "video_sink", "vlc :rtsp-caching=150 - --demux=h264");
            stunServers = getOrDefault(appProps, "stun_servers", "jitsi.org, numb.viagenie.ca, stun.ekiga.net");
            nodeSyncServer = getOrDefault(appProps, "sync_server_url", "http://some.server.net:3000");
            udpHoleThisName = getOrDefault(appProps, "udphole.this_name", "myName");
            udpHoleThatName = getOrDefault(appProps, "udphole.that_name", "thatName");
            plainUdpLocalPort = getOrDefault(appProps, "plainudp.local_port", 27015);
            plainUdpRemoteAddress = getOrDefault(appProps, "plainudp.remote_address", "127.0.0.1:27015");
            mainChannel = getOrDefault(appProps, "main_channel", CHANNEL_TYPE_PLAIN_UDP);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            inputStream.close();
        }
    }

    static <T, M extends Hashtable<Object, Object>> T getOrDefault(M map, String key, T defaultValue) {
        T value = (T) map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }


}
