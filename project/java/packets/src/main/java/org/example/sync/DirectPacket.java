package org.example.sync;

import org.example.ByteUtils;
import org.example.DataReference;

/**
 * start[3], size[1], type[1], id[2], block[size-TLC_SIZE-HEADER_SIZE]
 */
class DirectPacket extends BasePacket {

    public static final int ID_OFFSET = BODY_OFFSET;
    public static final int BLOCK_OFFSET = ID_OFFSET + 2;
    public static final int HEADER_SIZE = 2;

    private final int id;
    private final DataReference block;

    public DirectPacket(int id, DataReference block) {
        this.id = id;
        this.block = block;
    }

    public int getId() {
        return id;
    }

    public DataReference getBlock() {
        return block;
    }

    public int calculateSize(){
        return TLC_SIZE + HEADER_SIZE + block.getLength();
    }

    public void toArray(byte[] buf, int offset) {
        int size = calculateSize();
        ByteUtils.bufToBuf(start, 0, start.length, buf, offset);
        ByteUtils.u8ToBuf(size, buf, offset + SIZE_OFFSET);
        ByteUtils.u8ToBuf(PacketTypes.DIRECT.value(), buf, offset + TYPE_OFFSET);
        ByteUtils.u16ToBuf(id, buf, offset + ID_OFFSET);
        ByteUtils.bufToBuf(block.getBuf(), block.getOffset(), block.getLength(), buf, offset + BLOCK_OFFSET);
    }

    public static DirectPacket fromIncomingData(byte[] buf, int offset) {
        int size = ByteUtils.bufToU8(buf, offset + SIZE_OFFSET);
        int blockSize = size - TLC_SIZE - HEADER_SIZE;
        int id = ByteUtils.bufToU16(buf, offset + ID_OFFSET);
        return new DirectPacket(id, new DataReference(buf,
                offset + BLOCK_OFFSET, blockSize));
    }

}
