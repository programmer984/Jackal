package org.example.services.videoconsumer;

import org.example.TimeUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One big videoframe is splitted to parts
 * This class holds these parts
 */
public class VideoFramePackets implements Comparable<VideoFramePackets> {
    final int id;
    final int partsCount;
    final int approxSize;
    final long creationTimestamp = TimeUtils.nowMs();
    boolean isIframe;
    private final Set<PacketDecorator> parts = new HashSet<>();


    public VideoFramePackets(PacketDecorator some) {
        this.id = some.id;
        this.partsCount = some.partsCount;
        approxSize = some.approxSize;
    }



    public void addPart(PacketDecorator part) {
        parts.add(part);
        isIframe = part.frameConfig.iFrame;
    }

    public boolean isComplete() {
        return parts.size() >= partsCount;
    }



    public int getId() {
        return id;
    }

    @Override
    public int compareTo(VideoFramePackets o) {
        return Integer.compare(id, o.id);
    }

    @Override
    public String toString() {
        String logIds = parts.stream().map(p1 -> String.valueOf(p1.logId))
                .collect(Collectors.joining(",", "", ""));
        String partIndexes = parts.stream().map(p1 -> String.valueOf(p1.partIndex))
                .collect(Collectors.joining(",", "", ""));
        long now = TimeUtils.nowMs();
        return String.format("%s-Frame version %d, complete %s, parts %s/%d,  logIds %s, delta %d ",
                (isIframe ? "I" : "P"),id, isComplete(), partIndexes, partsCount, logIds, now - creationTimestamp);
    }

    public Set<Integer> getLacks() {
        Set<Integer> lackParts = new HashSet<>();
        Set<Integer> presentPartsId = new HashSet<>(parts).stream()
                .map(PacketDecorator::getIndex).collect(Collectors.toSet());
        for (int i = 0; i < partsCount; i++) {
            if (presentPartsId.contains(i)) {
                continue;
            }
            lackParts.add(i);
        }
        return lackParts;
    }

    public Set<PacketDecorator> getParts() {
        return parts;
    }
}