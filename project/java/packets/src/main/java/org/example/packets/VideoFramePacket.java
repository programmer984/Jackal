package org.example.packets;

import org.example.ByteUtils;
import org.example.CommonConfig;

/**
 * 1 approximate size in kBytes
 * 4 version
 * 1 parts total
 * 1 part index
 * 4 part offset in original videoFrame
 * [] video data
 */
public class VideoFramePacket extends AbstractPacket {

    public static final int HEADER_LENGTH = 12;
    public static final int MTU = CommonConfig.PACKET_SIZE_PREFERRED - HEADER_LENGTH - TLC_LENGTH;

    private final int id;
    private final byte[] data;
    //used for calculation packet receiving time
    private final int totalApproximateSize;
    private final int partIndex;
    private final int partsCount;
    private final int partOffset;
    private final int partSize;

    private final byte frameConfig;

    public static final int APPROX_SIZE_OFFSET = BODY_OFFSET;
    public static final int ID_OFFSET = APPROX_SIZE_OFFSET + 1;
    public static final int PARTS_COUNT = ID_OFFSET + 4;
    public static final int PART_INDEX = PARTS_COUNT + 1;
    public static final int FRAME_CONFIG = PART_INDEX + 1;
    public static final int VIDEO_DATA_OFFSET = FRAME_CONFIG + 1;

    public VideoFramePacket(int id, byte[] videFrame, int partIndex, int partsCount, int partOffset, int partSize, boolean isIFrame) {
        super(PacketTypes.VideoFrame);
        this.id = id;
        this.data = videFrame;
        this.partIndex = partIndex;
        this.partsCount = partsCount;
        this.partOffset = partOffset;
        this.partSize = partSize;
        this.frameConfig = new FrameConfig(isIFrame).toByte();
        totalApproximateSize = videFrame.length / 1000;
    }

    public int getId() {
        return id;
    }

    public int getIndex() {
        return partIndex;
    }

    public static int getPacketId(byte[] packets, int packetOffset) {
        return ByteUtils.bufToI32(packets, packetOffset + ID_OFFSET);
    }

    public static int getVideFrameSize(byte[] packets, int packetOffset) {
        return getSize(packets, packetOffset) - HEADER_LENGTH - TLC_LENGTH;
    }

    public static int getVideoDataOffset(int packetOffset) {
        return packetOffset + VIDEO_DATA_OFFSET;
    }

    public static int getPartsCount(byte[] packets, int packetOffset) {
        return ByteUtils.bufToU8(packets, packetOffset + PARTS_COUNT);
    }

    public static int getPartIndex(byte[] packets, int packetOffset) {
        return ByteUtils.bufToU8(packets, packetOffset + PART_INDEX);
    }

    public static int getApproxSize(byte[] packets, int packetOffset) {
        return ByteUtils.bufToU8(packets, packetOffset + APPROX_SIZE_OFFSET);
    }

    public static boolean isIframe(byte[] packets, int packetOffset) {
        int i = getVideoDataOffset(packetOffset);
        byte[] data = packets;
        //00 00 00 01 (? & 0x1F = 5)
        if (packets[i + 2] == 0 && packets[i + 3] == 1 && (packets[i + 4] & 0x1F) == 5) {
            return true;
        }
        return false;
    }

    public static FrameConfig getFrameConfig(byte[] packets, int packetOffset) {
        byte configByte = packets[packetOffset + FRAME_CONFIG];
        return FrameConfig.fromByte(configByte);
    }


    @Override
    public void toArray(byte[] buf, int packetOffset, int calculatedSize) {

        ByteUtils.u8ToBuf(totalApproximateSize, buf, packetOffset + APPROX_SIZE_OFFSET);
        ByteUtils.i32ToBuf(id, buf, packetOffset + ID_OFFSET);
        ByteUtils.u8ToBuf(partsCount, buf, packetOffset + PARTS_COUNT);
        ByteUtils.u8ToBuf(partIndex, buf, packetOffset + PART_INDEX);
        ByteUtils.u8ToBuf(frameConfig, buf, packetOffset + FRAME_CONFIG);
        ByteUtils.bufToBuf(data, partOffset, partSize, buf, packetOffset + VIDEO_DATA_OFFSET);

        setTypeAndSize(buf, packetOffset, calculatedSize);
        setCrc(buf, packetOffset, calculatedSize);
    }

    @Override
    public int calculateSize() {
        return TLC_LENGTH + HEADER_LENGTH + partSize;
    }

    @Override
    public String getDescription() {
        return String.format("version %d part %d/%d", id, partIndex + 1, partsCount);
    }

    public static VideoFramePacket[] split(byte[] videoData, int versionId) {
        return split(videoData, versionId, MTU, false);
    }

    public static VideoFramePacket[] split(byte[] videoData, int versionId, boolean isIframe) {
        return split(videoData, versionId, MTU, isIframe);
    }

    public static VideoFramePacket[] split(byte[] videoData, int versionId, int mtu) {
        return split(videoData, versionId, mtu, false);
    }

    public static VideoFramePacket[] split(byte[] videoData, int versionId, int mtu, boolean isIframe) {
        int count = (int) Math.ceil((float) videoData.length / (float) mtu);
        VideoFramePacket[] result = new VideoFramePacket[count];
        for (int i = 0; i < count; i++) {
            int offset = i * mtu;
            int size = (i + 1 == count) ? videoData.length - offset : mtu;
            result[i] = new VideoFramePacket(versionId, videoData, i, count, offset, size, isIframe);
        }
        return result;
    }

    public static class FrameConfig {
        public boolean iFrame;

        public FrameConfig() {
        }

        public FrameConfig(boolean iFrame) {
            this.iFrame = iFrame;
        }

        public byte toByte() {
            int result = 0;
            if (iFrame) {
                result |= 1;
            }
            return (byte) result;
        }

        public static FrameConfig fromByte(byte b) {
            FrameConfig frameConfig = new FrameConfig();
            if ((b & 1) != 0) {
                frameConfig.iFrame = true;
            }
            return frameConfig;
        }
    }

}
