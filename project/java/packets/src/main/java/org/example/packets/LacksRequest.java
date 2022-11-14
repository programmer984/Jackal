package org.example.packets;

import org.example.ByteUtils;
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
 */
public class LacksRequest extends AbstractPacket {
    private final Map<Integer, Set<Integer>> lacks;

    public LacksRequest(Map<Integer, Set<Integer>> lacks) {
        super(PacketTypes.VideoLacksRequest);
        this.lacks = lacks;
    }

    @Override
    public void toArray(byte[] buf, int packetOffset, int calculatedSize) {
        int offset = packetOffset + BODY_OFFSET;
        for (Integer id : lacks.keySet()) {
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
        int bodySize = 0;
        for (Integer id : lacks.keySet()) {
            bodySize += 5;
            bodySize += lacks.get(id).size();
        }
        return TLC_LENGTH + bodySize;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        for (Integer id : lacks.keySet()) {
            sb.append("id = ");
            sb.append(id);
            sb.append("; parts: ");
            sb.append(lacks.get(id).stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
            sb.append(". ");
        }
        return sb.toString();
    }

    public static Map<Integer, Set<Integer>> getLacks(byte[] packets, int packetOffset) {
        Map<Integer, Set<Integer>> result = new HashMap<>();
        int size = getSize(packets, packetOffset);
        int maxOffset = packetOffset + size - TLC_LENGTH - 1;
        int offset = packetOffset + BODY_OFFSET;
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
