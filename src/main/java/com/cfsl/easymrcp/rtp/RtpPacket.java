package com.cfsl.easymrcp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Arrays;
import java.util.Objects;

public final class RtpPacket {
    private static final int RTP_HEADER_LENGTH = 12;
    private static final int CSRC_LENGTH = 4;
    private static final int EXTENSION_HEADER_LENGTH = 4;

    private final int payloadType;
    private final int sequenceNumber;
    private final long timestamp;
    private final byte[] payload;

    private RtpPacket(int payloadType, int sequenceNumber, long timestamp, byte[] payload) {
        this(payloadType, sequenceNumber, timestamp, payload, true);
    }

    private RtpPacket(int payloadType, int sequenceNumber, long timestamp, byte[] payload, boolean copyPayload) {
        validatePayloadType(payloadType);
        validateSequenceNumber(sequenceNumber);
        validateTimestamp(timestamp);
        this.payloadType = payloadType;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        byte[] checkedPayload = Objects.requireNonNull(payload, "payload must not be null");
        this.payload = copyPayload ? Arrays.copyOf(checkedPayload, checkedPayload.length) : checkedPayload;
    }

    public static RtpPacket parse(ByteBuf byteBuf) {
        Objects.requireNonNull(byteBuf, "byteBuf must not be null");
        int length = byteBuf.readableBytes();
        if (length < RTP_HEADER_LENGTH) {
            throw new IllegalArgumentException("RTP packet must be at least 12 bytes");
        }

        int readerIndex = byteBuf.readerIndex();
        int firstByte = byteBuf.getByte(readerIndex) & 0xFF;
        int csrcCount = firstByte & 0x0F;
        boolean hasExtension = (firstByte & 0x10) != 0;
        boolean hasPadding = (firstByte & 0x20) != 0;
        int headerLength = RTP_HEADER_LENGTH + (csrcCount * CSRC_LENGTH);
        if (length < headerLength) {
            throw malformedPacket("CSRC entries exceed packet length");
        }

        if (hasExtension) {
            if (length < headerLength + EXTENSION_HEADER_LENGTH) {
                throw malformedPacket("extension header exceeds packet length");
            }
            int extensionLengthWords = ((byteBuf.getByte(readerIndex + headerLength + 2) & 0xFF) << 8)
                    | (byteBuf.getByte(readerIndex + headerLength + 3) & 0xFF);
            int extensionLength = EXTENSION_HEADER_LENGTH + (extensionLengthWords * 4);
            if (length < headerLength + extensionLength) {
                throw malformedPacket("extension data exceeds packet length");
            }
            headerLength += extensionLength;
        }

        int payloadLength = length - headerLength;
        if (hasPadding) {
            int paddingLength = byteBuf.getByte(readerIndex + length - 1) & 0xFF;
            if (paddingLength == 0 || paddingLength > payloadLength) {
                throw malformedPacket("padding exceeds payload bounds");
            }
            payloadLength -= paddingLength;
        }
        if (payloadLength < 0) {
            throw malformedPacket("payload bounds are invalid");
        }

        int payloadType = byteBuf.getByte(readerIndex + 1) & 0x7F;
        int sequenceNumber = ((byteBuf.getByte(readerIndex + 2) & 0xFF) << 8)
                | (byteBuf.getByte(readerIndex + 3) & 0xFF);
        long timestamp = ((long) (byteBuf.getByte(readerIndex + 4) & 0xFF) << 24)
                | ((long) (byteBuf.getByte(readerIndex + 5) & 0xFF) << 16)
                | ((long) (byteBuf.getByte(readerIndex + 6) & 0xFF) << 8)
                | (byteBuf.getByte(readerIndex + 7) & 0xFFL);

        byte[] payload = new byte[payloadLength];
        byteBuf.getBytes(readerIndex + headerLength, payload);
        return new RtpPacket(payloadType, sequenceNumber, timestamp, payload, false);
    }

    public static RtpPacket parseRtpHeader(byte[] data, int length) {
        Objects.requireNonNull(data, "data must not be null");
        if (length < RTP_HEADER_LENGTH) {
            throw new IllegalArgumentException("RTP packet must be at least 12 bytes");
        }
        if (length > data.length) {
            throw new IllegalArgumentException("RTP packet length exceeds buffer size");
        }
        return parse(Unpooled.wrappedBuffer(data, 0, length));
    }

    public static RtpPacket of(int payloadType, int sequenceNumber, long timestamp, byte[] payload) {
        return new RtpPacket(payloadType, sequenceNumber, timestamp, payload);
    }

    public static RtpPacket silenceLike(RtpPacket source, int sequenceNumber, byte[] payload) {
        Objects.requireNonNull(source, "source must not be null");
        return new RtpPacket(source.payloadType, sequenceNumber, source.timestamp, payload);
    }

    public static RtpPacket silenceLike(RtpPacket source, int sequenceNumber, long timestamp, byte[] payload) {
        Objects.requireNonNull(source, "source must not be null");
        return new RtpPacket(source.payloadType, sequenceNumber, timestamp, payload);
    }

    public int getPayloadType() {
        return payloadType;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    byte[] payloadView() {
        return payload;
    }

    private static void validatePayloadType(int payloadType) {
        if (payloadType < 0 || payloadType > 127) {
            throw new IllegalArgumentException("payloadType must be between 0 and 127");
        }
    }

    private static void validateSequenceNumber(int sequenceNumber) {
        if (sequenceNumber < 0 || sequenceNumber > 0xFFFF) {
            throw new IllegalArgumentException("sequenceNumber must be between 0 and 65535");
        }
    }

    private static void validateTimestamp(long timestamp) {
        if (timestamp < 0 || timestamp > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("timestamp must be between 0 and 4294967295");
        }
    }

    private static IllegalArgumentException malformedPacket(String message) {
        return new IllegalArgumentException("Malformed RTP packet: " + message);
    }
}
