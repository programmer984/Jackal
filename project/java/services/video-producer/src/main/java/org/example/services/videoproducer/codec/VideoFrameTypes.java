package org.example.services.videoproducer.codec;

public enum VideoFrameTypes {
    videoFrameTypeInvalid,
    VideoFrameTypeIDR,        /// < IDR frame in H.264
    VideoFrameTypeI,          /// < I frame type
    VideoFrameTypeP,          /// < P frame type
    VideoFrameTypeSkip,       ///< skip the frame based encoder kernel
    VideoFrameTypeIPMixed,     ///< a frame where I and P slices are mixing, not supported yet
    Unknown;
    public static final int size = 6;


    public byte toByte(){
        return (byte)ordinal();
    }

    public static VideoFrameTypes of(byte b) {
        if (b >= 6) {
            return Unknown;
        }
        return values()[b];
    }
}
