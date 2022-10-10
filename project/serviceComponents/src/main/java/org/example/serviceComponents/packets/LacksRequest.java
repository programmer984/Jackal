package org.example.serviceComponents.packets;

import org.example.Configuration;
import org.example.Utils;

import java.nio.ByteBuffer;
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
    public byte[] toArray() {
        //calc body size
        int bodySize = 0;
        for (Integer id : lacks.keySet()) {
            bodySize += 5;
            bodySize += lacks.get(id).size();
        }

        byte[] buf = new byte[Utils.getPaddedSize(bodySize+TLC_LENGTH)];
        int offset = BODY_OFFSET;
        for (Integer id : lacks.keySet()) {
            Set<Integer> parts = lacks.get(id);
            Utils.u8ToBuf(parts.size(), buf, offset++);
            Utils.i32ToBuf(id, buf, offset);
            offset += 4;
            for (int partIndex : parts) {
                Utils.u8ToBuf(partIndex, buf, offset++);
            }
        }
        int originalSize = bodySize + TLC_LENGTH;
        setTypeAndSize(buf, originalSize);
        setCrc(buf, originalSize);
        return buf;
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
            int lacksCount = Utils.bufToU8(packets, offset++);
            int id = Utils.bufToI32(packets, offset);
            offset += 4;
            Set<Integer> lacks = new HashSet<>();
            result.put(id, lacks);
            for (int i = 0; i < lacksCount; i++) {
                lacks.add(Utils.bufToU8(packets, offset++));
            }
        }
        return result;
    }
}
