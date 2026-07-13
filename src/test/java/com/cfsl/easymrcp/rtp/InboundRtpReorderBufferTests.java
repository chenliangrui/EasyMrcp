package com.cfsl.easymrcp.rtp;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundRtpReorderBufferTests {

    private static final int PAYLOAD_TYPE = AudioCodecUtil.PT_PCMA;
    private static final int DEFAULT_PAYLOAD_SIZE = 4;

    @Test
    void buffersUntilWindowDepthReachedThenStartsDrainingInOrder() {
        InboundRtpReorderBuffer buffer = new InboundRtpReorderBuffer(PAYLOAD_TYPE, DEFAULT_PAYLOAD_SIZE, 3, 3, 160L);

        assertTrue(offer(buffer, packet(100)).isEmpty());
        assertTrue(offer(buffer, packet(101)).isEmpty());

        List<RtpPacket> emitted = offer(buffer, packet(102));

        assertSequences(emitted, 100, 101, 102);
        assertEquals(0, buffer.getDuplicateCount());
        assertEquals(0, buffer.getLateCount());
        assertEquals(0, buffer.getReorderedCount());
        assertEquals(0, buffer.getLossFillCount());
    }

    @Test
    void outOfOrderPacketWaitsForMissingPacketBeforeLookaheadIsFull() {
        InboundRtpReorderBuffer buffer = new InboundRtpReorderBuffer(PAYLOAD_TYPE, DEFAULT_PAYLOAD_SIZE, 2, 3, 160L);

        assertTrue(offer(buffer, packet(100)).isEmpty());
        assertSequences(offer(buffer, packet(101)), 100, 101);

        assertTrue(offer(buffer, packet(103)).isEmpty());
        assertEquals(1, buffer.getReorderedCount());

        assertSequences(offer(buffer, packet(102)), 102, 103);
    }

    @Test
    void missingPacketIsSynthesizedAfterLookaheadWindowIsSatisfied() {
        InboundRtpReorderBuffer buffer = new InboundRtpReorderBuffer(PAYLOAD_TYPE, DEFAULT_PAYLOAD_SIZE, 3, 3, 160L);

        assertTrue(offer(buffer, packet(100)).isEmpty());
        assertTrue(offer(buffer, packet(101)).isEmpty());
        assertSequences(offer(buffer, packet(103)), 100, 101);
        assertTrue(offer(buffer, packet(104)).isEmpty());

        List<RtpPacket> emitted = offer(buffer, packet(105));

        assertSequences(emitted, 102, 103, 104, 105);
        assertSilence(emitted.get(0), 102, (byte) 0xD5);
        assertEquals(1, buffer.getLossFillCount());
    }

    @Test
    void silenceTimestamp_shouldUseConfiguredTimestampStep() throws Exception {
        int l16PayloadSize = 320;
        InboundRtpReorderBuffer buffer = newBuffer(97, l16PayloadSize, 0, 3, 160L);

        assertSequences(offer(buffer, packet(100, 1000L, l16PayloadSize)), 100);

        List<RtpPacket> emitted = offer(buffer, packet(102, 1640L, l16PayloadSize));

        assertSequences(emitted, 101, 102);
        assertEquals(1160L, emitted.get(0).getTimestamp());
        assertSilence(emitted.get(0), 97, 101, (byte) 0x00, l16PayloadSize);
    }

    @Test
    void zeroWindowTreatsFirstVisibleGapAsLostImmediately() {
        InboundRtpReorderBuffer buffer = new InboundRtpReorderBuffer(PAYLOAD_TYPE, DEFAULT_PAYLOAD_SIZE, 0, 3, 160L);

        assertSequences(offer(buffer, packet(100)), 100);

        List<RtpPacket> emitted = offer(buffer, packet(102));

        assertSequences(emitted, 101, 102);
        assertSilence(emitted.get(0), 101, (byte) 0xD5);
        assertEquals(1, buffer.getLossFillCount());
    }

    @Test
    void consecutiveLossFillBudgetTriggersResyncToEarliestBufferedPacket() {
        InboundRtpReorderBuffer buffer = new InboundRtpReorderBuffer(PAYLOAD_TYPE, DEFAULT_PAYLOAD_SIZE, 0, 2, 160L);

        assertSequences(offer(buffer, packet(100)), 100);

        List<RtpPacket> emitted = offer(buffer, packet(105));

        assertSequences(emitted, 101, 102, 105);
        assertSilence(emitted.get(0), 101, (byte) 0xD5);
        assertSilence(emitted.get(1), 102, (byte) 0xD5);
        assertEquals(2, buffer.getLossFillCount());
    }

    @Test
    void duplicateBufferedPacketsAndLatePacketsAreDropped() {
        InboundRtpReorderBuffer buffer = new InboundRtpReorderBuffer(PAYLOAD_TYPE, DEFAULT_PAYLOAD_SIZE, 2, 3, 160L);

        assertTrue(offer(buffer, packet(100)).isEmpty());
        assertSequences(offer(buffer, packet(101)), 100, 101);
        assertTrue(offer(buffer, packet(104)).isEmpty());
        assertTrue(offer(buffer, packet(104)).isEmpty());
        assertTrue(offer(buffer, packet(101)).isEmpty());

        assertEquals(1, buffer.getDuplicateCount());
        assertEquals(1, buffer.getLateCount());
    }

    @Test
    void sequenceWrapAroundKeepsExpectedSequenceOrdering() {
        InboundRtpReorderBuffer buffer = new InboundRtpReorderBuffer(PAYLOAD_TYPE, DEFAULT_PAYLOAD_SIZE, 2, 3, 160L);

        assertTrue(offer(buffer, packet(65535)).isEmpty());

        List<RtpPacket> emitted = offer(buffer, packet(0));

        assertSequences(emitted, 65535, 0);
    }

    private static void assertSequences(List<RtpPacket> packets, int... expectedSequences) {
        assertEquals(expectedSequences.length, packets.size());
        for (int i = 0; i < expectedSequences.length; i++) {
            assertEquals(expectedSequences[i], packets.get(i).getSequenceNumber());
        }
    }

    private static void assertSilence(RtpPacket packet, int sequenceNumber, byte fillByte) {
        assertSilence(packet, PAYLOAD_TYPE, sequenceNumber, fillByte, DEFAULT_PAYLOAD_SIZE);
    }

    private static void assertSilence(RtpPacket packet, int payloadType, int sequenceNumber, byte fillByte, int payloadSize) {
        assertEquals(payloadType, packet.getPayloadType());
        assertEquals(sequenceNumber, packet.getSequenceNumber());
        byte[] expectedPayload = new byte[payloadSize];
        for (int i = 0; i < expectedPayload.length; i++) {
            expectedPayload[i] = fillByte;
        }
        assertArrayEquals(expectedPayload, packet.getPayload());
    }

    private static RtpPacket packet(int sequenceNumber) {
        return RtpPacket.of(PAYLOAD_TYPE, sequenceNumber, sequenceNumber * 160L, new byte[]{0x01, 0x02, 0x03, 0x04});
    }

    private static RtpPacket packet(int sequenceNumber, long timestamp, int payloadSize) {
        return RtpPacket.of(97, sequenceNumber, timestamp, new byte[payloadSize]);
    }

    private static InboundRtpReorderBuffer newBuffer(int payloadType,
                                                     int defaultPayloadSize,
                                                     int reorderWindowPackets,
                                                     int maxConsecutiveLossFill,
                                                     long timestampStep) throws Exception {
        Constructor<InboundRtpReorderBuffer> constructor = InboundRtpReorderBuffer.class.getConstructor(
                int.class, int.class, int.class, int.class, long.class);
        return constructor.newInstance(
                payloadType, defaultPayloadSize, reorderWindowPackets, maxConsecutiveLossFill, timestampStep);
    }

    private static List<RtpPacket> offer(InboundRtpReorderBuffer buffer, RtpPacket packet) {
        List<RtpPacket> emitted = new ArrayList<>();
        buffer.offer(packet, emitted::add);
        return emitted;
    }
}
