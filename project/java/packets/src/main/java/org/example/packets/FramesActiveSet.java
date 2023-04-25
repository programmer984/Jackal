package org.example.packets;

import org.example.ByteUtils;
import org.example.DataReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Send every [20]ms our active set
 * and DataProvider will send to us lacks if required
 * [4] - marker (for easy log viewing)
 * [4] - last successful id
 * ordered
 * 1 parts count
 * 4 id
 * 1 part index
 * 1 part index...
 * 1 parts count
 * 4 id
 * 1 part index...
 * @see LacksRequest
 */
public class FramesActiveSet extends AbstractPacket {
    public static final int MARKER_OFFSET = BODY_OFFSET;
    public static final int LAST_SUCCESS_OFFSET = MARKER_OFFSET + 4;
    public static final int SET_OFFSET = LAST_SUCCESS_OFFSET + 4;
    private final Map<Integer, Set<Integer>> frames;
    private final int lastSuccessId;
    private final int marker;

    public FramesActiveSet(int marker, int lastSuccessId, Map<Integer, Set<Integer>> frames) {
        super(PacketTypes.VideoFramesSet);
        this.marker = marker;
        this.frames = frames;
        this.lastSuccessId = lastSuccessId;
    }

    @Override
    public void toArray(byte[] buf, int packetOffset, int calculatedSize) {
        int offset = packetOffset + MARKER_OFFSET;
        ByteUtils.i32ToBuf(marker, buf, offset);
        offset += 4;
        ByteUtils.i32ToBuf(lastSuccessId, buf, offset);
        offset += 4;
        for (Integer id : frames.keySet()
                .stream().sorted()
                .collect(Collectors.toList())) {
            Set<Integer> parts = frames.get(id);
            ByteUtils.u8ToBuf(parts.size(), buf, offset++);
            ByteUtils.i32ToBuf(id, buf, offset);
            offset += 4;
            for (int partIndex : parts) {
                ByteUtils.u8ToBuf(partIndex, buf, offset++);
            }
        }
        setTypeAndSize(buf, packetOffset, calculatedSize);
        setCrc(buf, packetOffset, calculatedSize);
    }

    @Override
    public int calculateSize() {
        //calc body size
        int bodySize = 8;
        for (Integer id : frames.keySet()) {
            bodySize += 5;
            bodySize += frames.get(id).size();
        }
        return TLC_LENGTH + bodySize;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Marker#");
        sb.append(marker);
        sb.append(" Last success Frame Id ");
        sb.append(lastSuccessId);
        sb.append(". ");
        for (Integer id : frames.keySet()) {
            sb.append("id = ");
            sb.append(id);
            sb.append("; parts: ");
            sb.append(frames.get(id).isEmpty() ? "all" : frames.get(id).stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
            sb.append(". ");
        }
        return sb.toString();
    }

    public int getMarker(){
        return marker;
    }

    public static int getMarkerFromBuf(DataReference dataReference) {
        return ByteUtils.bufToI32(dataReference.getBuf(), dataReference.getOffset() + MARKER_OFFSET);
    }

    public static int getLastSuccessId(DataReference dataReference) {
        byte[] packets = dataReference.getBuf();
        int packetOffset = dataReference.getOffset();
        return ByteUtils.bufToI32(packets, packetOffset + LAST_SUCCESS_OFFSET);
    }

    public static int getLastSuccessId(byte[] packets, int packetOffset) {
        return ByteUtils.bufToI32(packets, packetOffset + LAST_SUCCESS_OFFSET);
    }

    public static Map<Integer, Set<Integer>> getActiveSet(DataReference dataReference) {
        byte[] packets = dataReference.getBuf();
        int packetOffset = dataReference.getOffset();
        return getActiveSet(packets, packetOffset);
    }

    public static Map<Integer, Set<Integer>> getActiveSet(byte[] packets, int packetOffset) {
        Map<Integer, Set<Integer>> result = new HashMap<>();
        int size = getSize(packets, packetOffset);
        int maxOffset = packetOffset + size - TLC_LENGTH - 1;
        int offset = packetOffset + SET_OFFSET;
        while (offset < maxOffset) {
            int lacksCount = ByteUtils.bufToU8(packets, offset++);
            int id = ByteUtils.bufToI32(packets, offset);
            offset += 4;
            Set<Integer> lacks = new HashSet<>();
            result.put(id, lacks);
            for (int i = 0; i < lacksCount; i++) {
                lacks.add(ByteUtils.bufToU8(packets, offset++));
            }
        }
        return result;
    }
}
