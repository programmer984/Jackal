package org.example.stun;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class StunMessage {
    StunMessageType messageType = new StunMessageType();
    StunMessageLength messageLength = new StunMessageLength();
    StunMagicCookie magicCookie = new StunMagicCookie();
    StunTransactionId transactionId = new StunTransactionId();

    public byte[] generate() {
        ByteBuffer result  = ByteBuffer.allocate(20);
        messageType.applyTo(result, StunMessageType.OFFSET);
        messageLength.applyTo(result, StunMessageLength.OFFSET);
        magicCookie.applyTo(result, StunMagicCookie.OFFSET);
        transactionId.applyTo(result, StunTransactionId.OFFSET);
        return result.array();
    }
}


abstract class messagePart {

    abstract byte[] getBytes();

    public void applyTo(ByteBuffer target, int offset) {
        //запрашиваем с младшего бита по старший
        //со старшего байта по младший
        byte[] bytes = getBytes();
        int counter=0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            target.put(offset+counter++, bytes[i]);
        }
    }
}

/**
 * 14 бит
 */
class StunMessageType extends messagePart {
    public static final int OFFSET = 0;
    /*
    a Binding request has class=0b00 (request) and
method=0b000000000001 (Binding) and is encoded into the first 16 bits
as 0x0001
     */
    //Stun method 0x001: Binding
    short value = 0x0001;

    @Override
    byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(value);
        return buffer.array();
    }
}

/**
 * 16 бит
 */
class StunMessageLength extends messagePart {
    public static final int OFFSET = 2;
    short value = 0;

    @Override
    byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(value);
        return buffer.array();
    }
}

/**
 * 32 бит
 */
class StunMagicCookie extends messagePart {
    public static final int OFFSET = 4;
    int value = (int) (Math.random() * Integer.MAX_VALUE);

    @Override
    byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }
}

/**
 * 96 бит
 */
class StunTransactionId extends messagePart {
    public static final int OFFSET = 8;
    long value = (long) (Math.random() * Long.MAX_VALUE);

    @Override
    byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(value);
        return buffer.array();
    }
}