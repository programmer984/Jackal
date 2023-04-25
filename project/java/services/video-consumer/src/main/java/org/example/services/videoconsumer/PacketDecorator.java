package org.example.services.videoconsumer;

import org.example.endpoint.PacketReference;
import org.example.packets.VideoFramePacket;

public class PacketDecorator {

    private final PacketReference packet;
    private final int videoDataOffset;
    private final int videoDataLength;

    final int id;
    final Integer logId;

    final int partIndex;
    final int partsCount;
    final int approxSize;
    final VideoFramePacket.FrameConfig frameConfig;

    PacketDecorator(PacketReference packet, Integer logId) {
        this.packet = packet;
        this.logId = logId;
        final int packetOffset = packet.getOffset();
        final byte[] buf = packet.getData();
        videoDataOffset = VideoFramePacket.getVideoDataOffset(packetOffset);
        videoDataLength = VideoFramePacket.getVideFrameSize(buf, packetOffset);
        id = VideoFramePacket.getPacketId(buf, packetOffset);
        partIndex = VideoFramePacket.getPartIndex(buf, packetOffset);
        partsCount = VideoFramePacket.getPartsCount(buf, packetOffset);
        approxSize = VideoFramePacket.getApproxSize(buf, packetOffset);
        frameConfig = VideoFramePacket.getFrameConfig(buf, packetOffset);

    }

    int getIndex() {
        return partIndex;
    }

    public byte[] getBuffer() {
        return packet.getData();
    }

    public int getPacketOffset() {
        return packet.getOffset();
    }

    public int getVideDataOffset() {
        return videoDataOffset;
    }

    public int getVideDataLength() {
        return videoDataLength;
    }

    @Override
    public String toString() {
        return "packetDecorator{" +
                "videoDataOffset=" + videoDataOffset +
                ", id=" + id +
                ", logId=" + logId +
                ", videoDataOffset=" + videoDataOffset +
                ", partIndex=" + partIndex +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PacketDecorator that = (PacketDecorator) o;

        return partIndex == that.partIndex;
    }

    @Override
    public int hashCode() {
        return partIndex;
    }

}
