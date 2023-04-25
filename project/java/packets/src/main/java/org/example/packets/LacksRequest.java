package org.example.packets;

import org.example.ByteUtils;
import org.example.DataReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 1 lacks count
 * 4 id
 * 1 lack index
 * 1 lack index...
 * <p>
 * 1 lacks count
 * 4 id
 * etc...
 * if lacks count == 0 - we want all parts
 * it may happen when we received frame #156 but was awaiting #155
 * @see FramesActiveSet
 */
public class LacksRequest extends AbstractPacket {
    public static final int MARKER_OFFSET = BODY_OFFSET;
    public static final int SET_OFFSET = MARKER_OFFSET + 4;
    private final Map<Integer, Set<Integer>> lacks;
    private final int marker;

    public LacksRequest(int marker, Map<Integer, Set<Integer>> lacks) {
        super(PacketTypes.VideoLacksRequest);
        this.lacks = lacks;
        this.marker = marker;
    }

    public int getMarker(){
        return marker;
    }

    @Override
    public void toArray(byte[] buf, int packetOffset, int calculatedSize) {
        int offset = packetOffset + MARKER_OFFSET;
        ByteUtils.i32ToBuf(marker, buf, offset);
        offset += 4;
        for (Integer id : lacks.keySet()
                .stream().sorted()
                .collect(Collectors.toList())) {
            Set<Integer> parts = lacks.get(id);
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
        int bodySize = 4;
        for (Integer id : lacks.keySet()) {
            bodySize += 5;
            bodySize += lacks.get(id).size();
        }
        return TLC_LENGTH + bodySize;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Marker#");
        sb.append(marker);
        for (Integer id : lacks.keySet()) {
            sb.append(" Frame Id = ");
            sb.append(id);
            sb.append("; parts: ");
            sb.append(lacks.get(id).isEmpty() ? "all" : lacks.get(id).stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
            sb.append(". ");
        }
        return sb.toString();
    }

    public static int getMarkerFromBuf(DataReference dataReference) {
        return getMarkerFromBuf(dataReference.getBuf(), dataReference.getOffset());
    }

    public static int getMarkerFromBuf(byte[] packets, int packetOffset) {
        return ByteUtils.bufToI32(packets, packetOffset + MARKER_OFFSET);
    }

    public static Map<Integer, Set<Integer>> getLacks(DataReference dataReference) {
        byte[] packets = dataReference.getBuf();
        int packetOffset = dataReference.getOffset();
        return getLacks(packets, packetOffset);
    }

    public static Map<Integer, Set<Integer>> getLacks(byte[] packets, int packetOffset) {
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
