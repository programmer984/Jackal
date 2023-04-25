package org.example.packets;

import org.example.ByteUtils;

import java.nio.charset.StandardCharsets;

/**
 * It is used for logging purpose (not for a transmitting)
 */
public class LogFilePacketMarker extends AbstractPacket {

    private final byte[] asciiText;

    public LogFilePacketMarker(int logId, String direction) {
        super(PacketTypes.LogFilePacketMarker);
        asciiText = String.format("%s#%d#", direction, logId).getBytes(StandardCharsets.US_ASCII);
    }



    @Override
    public int calculateSize() {
        return TLC_LENGTH + asciiText.length;
    }

    @Override
    public void toArray(byte[] buf, int offset, int calculatedSize) {
        ByteUtils.bufToBuf(asciiText, 0, asciiText.length, buf, offset+BODY_OFFSET);
        setTypeAndSize(buf, offset, calculatedSize);
        setCrc(buf, offset, calculatedSize);
    }


    @Override
    public String getDescription() {
        return new String(asciiText, StandardCharsets.US_ASCII);
    }

    public static String getText(byte[] packets, int packetOffset) {
        int size = getSize(packets, packetOffset)-TLC_LENGTH;
        return new String(packets, packetOffset + BODY_OFFSET, size, StandardCharsets.US_ASCII);
    }
}