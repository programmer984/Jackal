package org.example.serviceComponents.packets;

import org.example.Utils;
import org.example.serviceComponents.imageCreating.ImagePart;
import org.example.serviceComponents.imageCreating.ImagePartsConfiguration;
import org.example.serviceComponents.imageParsing.ImageCell;

import java.util.Iterator;

/**
 * 1 - Horizontal direction
 * 1 - Horizontal power [%] (could be zero - then do not move)
 * 1 - Vertical direction
 * 1 - vertical power (could be zero - then do not move)
 * 1 - Time to move [ms] (for both axes)
 * 4 - version
 */
public class HWDoMove extends AbstractPacket {

    public enum HorizontalDirection {
        Idle,
        Left,
        Right
    }

    public enum VerticalDirection {
        Idle,
        Up,
        Down
    }

    public static class HorizontalCommand {
        HorizontalDirection direction;
        byte power;

        public HorizontalCommand(HorizontalDirection direction, byte power) {
            this.direction = direction;
            this.power = power;
        }

        @Override
        public String toString() {
            return "HorizontalCommand{" +
                    "direction=" + direction.name() +
                    ", power=" + (power & 0xFF) +
                    '}';
        }
    }

    public static class VerticalCommand {
        VerticalDirection direction;
        byte power;

        public VerticalCommand(VerticalDirection direction, byte power) {
            this.direction = direction;
            this.power = power;
        }

        @Override
        public String toString() {
            return "VerticalCommand{" +
                    "direction=" + direction.name() +
                    ", power=" +  (power & 0xFF) +
                    '}';
        }
    }

    private final HorizontalCommand horizontalCommand;
    private final VerticalCommand verticalCommand;
    private final byte time;
    private final long version;

    public HWDoMove(HorizontalCommand horizontalCommand, VerticalCommand verticalCommand, byte ms, long version) {
        super(PacketTypes.HWDoMove);
        this.horizontalCommand = horizontalCommand;
        this.verticalCommand = verticalCommand;
        this.time = ms;
        this.version = version;
    }

    @Override
    public byte[] toArray() {
        byte[] buf = new byte[TLC_LENGTH + 9];
        setTypeAndSize(buf);
        int offset = BODY_OFFSET;
        Utils.u8ToBuf(horizontalCommand.direction.ordinal(), buf, offset++);
        Utils.u8ToBuf(horizontalCommand.power, buf, offset++);
        Utils.u8ToBuf(verticalCommand.direction.ordinal(), buf, offset++);
        Utils.u8ToBuf(verticalCommand.power, buf, offset++);
        Utils.u8ToBuf(time, buf, offset++);
        Utils.i32ToBuf((int) version, buf, offset);
        setCrc(buf);
        return buf;
    }

    @Override
    public String toString() {
        return "HWDoMove{ " +
                 horizontalCommand +
                " " + verticalCommand +
                ", time=" + (time & 0xFF) +
                ", version=" + version +
                '}';
    }
}
