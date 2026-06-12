package com.cfsl.easymrcp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyAsrRtpProcessorTests {

    @Test
    void decodePayload_shouldDecodeG711PayloadUsingMediaType() throws Exception {
        byte[] pcmData = new byte[]{0x00, 0x00, (byte) 0xE8, 0x03, 0x18, (byte) 0xFC, 0x20, 0x00};

        assertDecodedG711(AudioCodecUtil.PT_PCMA, pcmData);
        assertDecodedG711(AudioCodecUtil.PT_PCMU, pcmData);
    }

    @Test
    void decodePayload_shouldConvertL16PayloadFromBigEndianToLittleEndian() throws Exception {
        byte[] l16Payload = new byte[]{0x00, 0x01, 0x01, 0x02};
        NettyAsrRtpProcessor processor = new NettyAsrRtpProcessor(97, l16Payload.length, 20);

        byte[] actual = invokeDecodePayload(processor, l16Payload);

        assertArrayEquals(new byte[]{0x01, 0x00, 0x02, 0x01}, actual);
    }

    @Test
    void constructor_shouldAcceptCustomReorderParameters() throws Exception {
        byte[] pcm100 = new byte[]{0x00, 0x00, 0x10, 0x00};
        byte[] pcm101 = new byte[]{0x20, 0x00, 0x30, 0x00};
        byte[] pcm102 = new byte[]{0x40, 0x00, 0x50, 0x00};
        byte[] payload100 = AudioCodecUtil.encode(pcm100, AudioCodecUtil.PT_PCMA);
        byte[] payload101 = AudioCodecUtil.encode(pcm101, AudioCodecUtil.PT_PCMA);
        byte[] payload102 = AudioCodecUtil.encode(pcm102, AudioCodecUtil.PT_PCMA);
        NettyAsrRtpProcessor processor = newConfiguredProcessor(AudioCodecUtil.PT_PCMA, payload100.length, 20, 3, 3);

        assertTrue(processIncomingPacket(processor, packet(100, payload100)).isEmpty());
        assertTrue(processIncomingPacket(processor, packet(101, payload101)).isEmpty());

        List<byte[]> emitted = processIncomingPacket(processor, packet(102, payload102));

        assertEquals(3, emitted.size());
    }

    @Test
    void processIncomingPacket_shouldDelayInitialEmissionUntilWindowIsFilled() throws Exception {
        byte[] pcm100 = new byte[]{0x00, 0x00, 0x10, 0x00};
        byte[] pcm101 = new byte[]{0x20, 0x00, 0x30, 0x00};
        byte[] pcm102 = new byte[]{0x40, 0x00, 0x50, 0x00};
        byte[] payload100 = AudioCodecUtil.encode(pcm100, AudioCodecUtil.PT_PCMA);
        byte[] payload101 = AudioCodecUtil.encode(pcm101, AudioCodecUtil.PT_PCMA);
        byte[] payload102 = AudioCodecUtil.encode(pcm102, AudioCodecUtil.PT_PCMA);
        NettyAsrRtpProcessor processor = newConfiguredProcessor(AudioCodecUtil.PT_PCMA, payload100.length, 20, 3, 3);

        assertTrue(processIncomingPacket(processor, packet(100, payload100)).isEmpty());
        assertTrue(processIncomingPacket(processor, packet(101, payload101)).isEmpty());

        List<byte[]> emitted = processIncomingPacket(processor, packet(102, payload102));

        assertEquals(3, emitted.size());
        assertArrayEquals(AudioCodecUtil.decode(payload100, AudioCodecUtil.PT_PCMA), emitted.get(0));
        assertArrayEquals(AudioCodecUtil.decode(payload101, AudioCodecUtil.PT_PCMA), emitted.get(1));
        assertArrayEquals(AudioCodecUtil.decode(payload102, AudioCodecUtil.PT_PCMA), emitted.get(2));
    }

    @Test
    void processIncomingPacket_shouldEmitDecodedBuffersDirectlyToConsumer() throws Exception {
        byte[] pcm100 = new byte[]{0x00, 0x00, 0x10, 0x00};
        byte[] pcm101 = new byte[]{0x20, 0x00, 0x30, 0x00};
        byte[] payload100 = AudioCodecUtil.encode(pcm100, AudioCodecUtil.PT_PCMA);
        byte[] payload101 = AudioCodecUtil.encode(pcm101, AudioCodecUtil.PT_PCMA);
        NettyAsrRtpProcessor processor = newConfiguredProcessor(AudioCodecUtil.PT_PCMA, payload100.length, 20, 2, 3);
        List<byte[]> emitted = new ArrayList<>();

        processIncomingPacket(processor, packet(100, payload100), emitted::add);
        processIncomingPacket(processor, packet(101, payload101), emitted::add);

        assertEquals(2, emitted.size());
        assertArrayEquals(AudioCodecUtil.decode(payload100, AudioCodecUtil.PT_PCMA), emitted.get(0));
        assertArrayEquals(AudioCodecUtil.decode(payload101, AudioCodecUtil.PT_PCMA), emitted.get(1));
    }

    @Test
    void processIncomingPacket_shouldOnlyFillGapAfterLookaheadWindowIsSatisfied() throws Exception {
        byte[] pcm100 = new byte[]{0x00, 0x00, 0x10, 0x00};
        byte[] pcm101 = new byte[]{0x20, 0x00, 0x30, 0x00};
        byte[] pcm103 = new byte[]{0x40, 0x00, 0x50, 0x00};
        byte[] pcm104 = new byte[]{0x60, 0x00, 0x70, 0x00};
        byte[] pcm105 = new byte[]{(byte) 0x80, 0x00, (byte) 0x90, 0x00};
        byte[] payload100 = AudioCodecUtil.encode(pcm100, AudioCodecUtil.PT_PCMA);
        byte[] payload101 = AudioCodecUtil.encode(pcm101, AudioCodecUtil.PT_PCMA);
        byte[] payload103 = AudioCodecUtil.encode(pcm103, AudioCodecUtil.PT_PCMA);
        byte[] payload104 = AudioCodecUtil.encode(pcm104, AudioCodecUtil.PT_PCMA);
        byte[] payload105 = AudioCodecUtil.encode(pcm105, AudioCodecUtil.PT_PCMA);
        NettyAsrRtpProcessor processor = newConfiguredProcessor(AudioCodecUtil.PT_PCMA, payload100.length, 20, 3, 3);

        assertTrue(processIncomingPacket(processor, packet(100, payload100)).isEmpty());
        assertTrue(processIncomingPacket(processor, packet(101, payload101)).isEmpty());

        List<byte[]> startEmission = processIncomingPacket(processor, packet(103, payload103));
        List<byte[]> noFillYet = processIncomingPacket(processor, packet(104, payload104));
        List<byte[]> gapFilled = processIncomingPacket(processor, packet(105, payload105));

        assertEquals(2, startEmission.size());
        assertArrayEquals(AudioCodecUtil.decode(payload100, AudioCodecUtil.PT_PCMA), startEmission.get(0));
        assertArrayEquals(AudioCodecUtil.decode(payload101, AudioCodecUtil.PT_PCMA), startEmission.get(1));
        assertTrue(noFillYet.isEmpty());
        assertEquals(4, gapFilled.size());
        byte[] silencePayload = new byte[payload100.length];
        for (int i = 0; i < silencePayload.length; i++) {
            silencePayload[i] = (byte) 0xD5;
        }
        assertArrayEquals(
                AudioCodecUtil.decode(silencePayload, AudioCodecUtil.PT_PCMA),
                gapFilled.get(0));
        assertArrayEquals(AudioCodecUtil.decode(payload103, AudioCodecUtil.PT_PCMA), gapFilled.get(1));
        assertArrayEquals(AudioCodecUtil.decode(payload104, AudioCodecUtil.PT_PCMA), gapFilled.get(2));
        assertArrayEquals(AudioCodecUtil.decode(payload105, AudioCodecUtil.PT_PCMA), gapFilled.get(3));
    }

    @Test
    void setRunFalse_shouldClearBufferedWindowState() throws Exception {
        byte[] pcm100 = new byte[]{0x00, 0x00, 0x10, 0x00};
        byte[] pcm101 = new byte[]{0x20, 0x00, 0x30, 0x00};
        byte[] pcm102 = new byte[]{0x40, 0x00, 0x50, 0x00};
        byte[] pcm103 = new byte[]{0x60, 0x00, 0x70, 0x00};
        byte[] pcm104 = new byte[]{(byte) 0x80, 0x00, (byte) 0x90, 0x00};
        byte[] payload100 = AudioCodecUtil.encode(pcm100, AudioCodecUtil.PT_PCMA);
        byte[] payload101 = AudioCodecUtil.encode(pcm101, AudioCodecUtil.PT_PCMA);
        byte[] payload102 = AudioCodecUtil.encode(pcm102, AudioCodecUtil.PT_PCMA);
        byte[] payload103 = AudioCodecUtil.encode(pcm103, AudioCodecUtil.PT_PCMA);
        byte[] payload104 = AudioCodecUtil.encode(pcm104, AudioCodecUtil.PT_PCMA);
        NettyAsrRtpProcessor processor = newConfiguredProcessor(AudioCodecUtil.PT_PCMA, payload100.length, 20, 2, 3);

        assertTrue(processIncomingPacket(processor, packet(100, payload100)).isEmpty());
        assertEquals(2, processIncomingPacket(processor, packet(101, payload101)).size());
        assertTrue(processIncomingPacket(processor, packet(103, payload103)).isEmpty());

        processor.setRun(false);
        processor.setRun(true);

        assertTrue(processIncomingPacket(processor, packet(102, payload102)).isEmpty());

        List<byte[]> afterResume = processIncomingPacket(processor, packet(104, payload104));

        assertEquals(1, afterResume.size());
        assertArrayEquals(AudioCodecUtil.decode(payload102, AudioCodecUtil.PT_PCMA), afterResume.get(0));
    }

    private static void assertDecodedG711(int mediaType, byte[] pcmData) throws Exception {
        byte[] encodedPayload = AudioCodecUtil.encode(pcmData, mediaType);
        NettyAsrRtpProcessor processor = new NettyAsrRtpProcessor(mediaType, encodedPayload.length, 20);

        byte[] actual = invokeDecodePayload(processor, encodedPayload);

        assertArrayEquals(AudioCodecUtil.decode(encodedPayload, mediaType), actual);
    }

    private static byte[] invokeDecodePayload(NettyAsrRtpProcessor processor, byte[] payload) throws Exception {
        Method method = NettyAsrRtpProcessor.class.getDeclaredMethod("decodePayload", byte[].class, ByteBufAllocator.class);
        method.setAccessible(true);

        ByteBuf result = null;
        try {
            result = (ByteBuf) method.invoke(processor, payload, ByteBufAllocator.DEFAULT);
            return readBytes(result);
        } finally {
            if (result != null) {
                result.release();
            }
        }
    }

    private static List<byte[]> processIncomingPacket(NettyAsrRtpProcessor processor, RtpPacket packet) throws Exception {
        List<byte[]> emitted = new ArrayList<>();
        processIncomingPacket(processor, packet, emitted::add);
        return emitted;
    }

    @SuppressWarnings("unchecked")
    private static void processIncomingPacket(NettyAsrRtpProcessor processor,
                                              RtpPacket packet,
                                              Consumer<byte[]> consumer) throws Exception {
        Method method = NettyAsrRtpProcessor.class.getDeclaredMethod(
                "processIncomingPacket", RtpPacket.class, ByteBufAllocator.class, Consumer.class);
        method.setAccessible(true);
        method.invoke(processor, packet, ByteBufAllocator.DEFAULT, (Consumer<ByteBuf>) buffer -> {
            try {
                consumer.accept(readBytes(buffer));
            } finally {
                buffer.release();
            }
        });
    }

    private static NettyAsrRtpProcessor newConfiguredProcessor(int mediaType,
                                                               int frameBytes,
                                                               int sendIntervalMs,
                                                               int reorderWindowPackets,
                                                               int maxConsecutiveLossFill) throws Exception {
        Constructor<NettyAsrRtpProcessor> constructor = NettyAsrRtpProcessor.class.getConstructor(
                int.class, int.class, int.class, int.class, int.class);
        return constructor.newInstance(
                mediaType, frameBytes, sendIntervalMs, reorderWindowPackets, maxConsecutiveLossFill);
    }

    private static byte[] readBytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

    private static RtpPacket packet(int sequenceNumber, byte[] payload) {
        return RtpPacket.of(AudioCodecUtil.PT_PCMA, sequenceNumber, sequenceNumber * 160L, payload);
    }
}
