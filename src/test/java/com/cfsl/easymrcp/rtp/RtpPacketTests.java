package com.cfsl.easymrcp.rtp;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RtpPacketTests {

    @Test
    void parseExposesHeaderFieldsAndPayload() {
        byte[] payload = new byte[]{0x11, 0x22, 0x33};
        byte[] packetBytes = buildPacket(96, 0xFFEE, 0xFEDCBA98L, payload);

        RtpPacket packet = RtpPacket.parse(Unpooled.wrappedBuffer(packetBytes));

        assertEquals(96, packet.getPayloadType());
        assertEquals(0xFFEE, packet.getSequenceNumber());
        assertEquals(0xFEDCBA98L, packet.getTimestamp());
        assertArrayEquals(payload, packet.getPayload());
        assertNotSame(payload, packet.getPayload());
        byte[] extractedPayload = packet.getPayload();
        extractedPayload[0] = 0x00;
        assertArrayEquals(payload, packet.getPayload());
    }

    @Test
    void parseRejectsPacketsShorterThanHeader() {
        assertThrows(IllegalArgumentException.class,
                () -> RtpPacket.parse(Unpooled.wrappedBuffer(new byte[11])));
    }

    @Test
    void parseRtpHeaderUsesProvidedLengthAndReturnsParsedPacket() {
        byte[] payload = new byte[]{0x21, 0x22};
        byte[] packetBytes = buildPacket(18, 1234, 0x01020304L, payload);
        byte[] largerBuffer = new byte[packetBytes.length + 3];
        System.arraycopy(packetBytes, 0, largerBuffer, 0, packetBytes.length);
        largerBuffer[packetBytes.length] = 0x66;

        RtpPacket packet = RtpPacket.parseRtpHeader(largerBuffer, packetBytes.length);

        assertEquals(18, packet.getPayloadType());
        assertEquals(1234, packet.getSequenceNumber());
        assertEquals(0x01020304L, packet.getTimestamp());
        assertArrayEquals(payload, packet.getPayload());
    }

    @Test
    void parseHandlesCsrcExtensionAndPaddingWhenExtractingPayload() {
        byte[] payload = new byte[]{0x31, 0x32, 0x33};
        byte[] packetBytes = buildPacketWithOptionalParts(
                0xB2,
                101,
                7,
                0x0A0B0C0DL,
                new int[]{0x11111111, 0x22222222},
                new byte[]{0x12, 0x34, 0x00, 0x01, 0x55, 0x66, 0x77, 0x44},
                payload,
                2
        );

        RtpPacket packet = RtpPacket.parse(Unpooled.wrappedBuffer(packetBytes));

        assertEquals(101, packet.getPayloadType());
        assertEquals(7, packet.getSequenceNumber());
        assertEquals(0x0A0B0C0DL, packet.getTimestamp());
        assertArrayEquals(payload, packet.getPayload());
    }

    @Test
    void parseRejectsMalformedOptionalHeaderBounds() {
        byte[] packetBytes = buildPacketWithOptionalParts(
                0x90,
                0,
                1,
                2L,
                new int[0],
                new byte[]{0x10, 0x00, 0x00, 0x01},
                new byte[]{0x44},
                0
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RtpPacket.parse(Unpooled.wrappedBuffer(packetBytes, 0, packetBytes.length - 2)));
        assertTrue(ex.getMessage().contains("Malformed"));
    }

    @Test
    void silenceLikeReusesMetadataAndReplacesSequenceNumberAndPayload() {
        byte[] sourcePayload = new byte[]{0x01, 0x02};
        RtpPacket source = RtpPacket.of(97, 10, 0x10203040L, sourcePayload);
        byte[] replacementPayload = new byte[]{0x55, 0x66, 0x77};

        RtpPacket silence = RtpPacket.silenceLike(source, 0xFFFF, replacementPayload);

        assertEquals(97, silence.getPayloadType());
        assertEquals(0xFFFF, silence.getSequenceNumber());
        assertEquals(0x10203040L, silence.getTimestamp());
        assertArrayEquals(replacementPayload, silence.getPayload());
        assertNotSame(replacementPayload, silence.getPayload());
        byte[] silencePayload = silence.getPayload();
        silencePayload[0] = 0x00;
        assertArrayEquals(replacementPayload, silence.getPayload());
    }

    @Test
    void ofDefensivelyCopiesInputPayload() {
        byte[] payload = new byte[]{0x41, 0x42};

        RtpPacket packet = RtpPacket.of(0, 1, 2L, payload);
        payload[0] = 0x00;

        assertArrayEquals(new byte[]{0x41, 0x42}, packet.getPayload());
    }

    @Test
    void payloadViewReusesSingleInternalPayloadArrayForPackageConsumers() {
        byte[] payload = new byte[]{0x41, 0x42};

        RtpPacket packet = RtpPacket.of(0, 1, 2L, payload);
        byte[] firstView = packet.payloadView();
        byte[] secondView = packet.payloadView();

        assertArrayEquals(new byte[]{0x41, 0x42}, firstView);
        assertNotSame(payload, firstView);
        assertSame(firstView, secondView);
    }

    @Test
    void silenceLikeDefensivelyCopiesInputPayload() {
        RtpPacket source = RtpPacket.of(8, 2, 3L, new byte[]{0x01});
        byte[] payload = new byte[]{0x51, 0x52};

        RtpPacket packet = RtpPacket.silenceLike(source, 4, payload);
        payload[0] = 0x00;

        assertArrayEquals(new byte[]{0x51, 0x52}, packet.getPayload());
    }

    @Test
    void silenceLikeCanOverrideTimestamp() {
        RtpPacket source = RtpPacket.of(8, 2, 3L, new byte[]{0x01});

        RtpPacket packet = RtpPacket.silenceLike(source, 4, 99L, new byte[]{0x51, 0x52});

        assertEquals(8, packet.getPayloadType());
        assertEquals(4, packet.getSequenceNumber());
        assertEquals(99L, packet.getTimestamp());
        assertArrayEquals(new byte[]{0x51, 0x52}, packet.getPayload());
    }

    @Test
    void factoriesRejectOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.of(-1, 0, 0L, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.of(128, 0, 0L, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.of(0, -1, 0L, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.of(0, 65536, 0L, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.of(0, 0, -1L, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.of(0, 0, 0x1_0000_0000L, new byte[0]));
    }

    @Test
    void helpersRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> RtpPacket.parse(null));
        assertThrows(NullPointerException.class, () -> RtpPacket.of(0, 0, 0L, null));
        assertThrows(NullPointerException.class, () -> RtpPacket.silenceLike(null, 0, new byte[0]));
        assertThrows(NullPointerException.class, () -> RtpPacket.silenceLike(RtpPacket.of(0, 0, 0L, new byte[0]), 0, null));
    }

    private static byte[] buildPacket(int payloadType, int sequenceNumber, long timestamp, byte[] payload) {
        byte[] packet = new byte[12 + payload.length];
        packet[0] = (byte) 0x80;
        packet[1] = (byte) (payloadType & 0x7F);
        packet[2] = (byte) ((sequenceNumber >>> 8) & 0xFF);
        packet[3] = (byte) (sequenceNumber & 0xFF);
        packet[4] = (byte) ((timestamp >>> 24) & 0xFF);
        packet[5] = (byte) ((timestamp >>> 16) & 0xFF);
        packet[6] = (byte) ((timestamp >>> 8) & 0xFF);
        packet[7] = (byte) (timestamp & 0xFF);
        System.arraycopy(payload, 0, packet, 12, payload.length);
        return packet;
    }

    private static byte[] buildPacketWithOptionalParts(int firstByte,
                                                       int payloadType,
                                                       int sequenceNumber,
                                                       long timestamp,
                                                       int[] csrcs,
                                                       byte[] extensionData,
                                                       byte[] payload,
                                                       int paddingSize) {
        int extensionLength = extensionData.length;
        int totalLength = 12 + (csrcs.length * 4) + extensionLength + payload.length + paddingSize;
        byte[] packet = new byte[totalLength];
        packet[0] = (byte) firstByte;
        packet[1] = (byte) (payloadType & 0x7F);
        packet[2] = (byte) ((sequenceNumber >>> 8) & 0xFF);
        packet[3] = (byte) (sequenceNumber & 0xFF);
        packet[4] = (byte) ((timestamp >>> 24) & 0xFF);
        packet[5] = (byte) ((timestamp >>> 16) & 0xFF);
        packet[6] = (byte) ((timestamp >>> 8) & 0xFF);
        packet[7] = (byte) (timestamp & 0xFF);

        int offset = 12;
        for (int csrc : csrcs) {
            packet[offset] = (byte) ((csrc >>> 24) & 0xFF);
            packet[offset + 1] = (byte) ((csrc >>> 16) & 0xFF);
            packet[offset + 2] = (byte) ((csrc >>> 8) & 0xFF);
            packet[offset + 3] = (byte) (csrc & 0xFF);
            offset += 4;
        }

        if (extensionLength > 0) {
            System.arraycopy(extensionData, 0, packet, offset, extensionLength);
            offset += extensionLength;
        }

        System.arraycopy(payload, 0, packet, offset, payload.length);
        offset += payload.length;

        for (int i = 0; i < paddingSize; i++) {
            packet[offset + i] = 0x00;
        }
        if (paddingSize > 0) {
            packet[packet.length - 1] = (byte) paddingSize;
        }
        return packet;
    }
}
