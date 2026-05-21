package com.cfsl.easymrcp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NettyAsrRtpProcessorTests {

    @Test
    void processRtpData_shouldDecodeG711PayloadUsingMediaType() throws Exception {
        assertDecodedG711(AudioCodecUtil.PT_PCMA);
        assertDecodedG711(AudioCodecUtil.PT_PCMU);
    }

    @Test
    void processRtpData_shouldConvertL16PayloadFromBigEndianToLittleEndian() throws Exception {
        NettyAsrRtpProcessor processor = new NettyAsrRtpProcessor(97);
        byte[] l16Payload = new byte[]{0x00, 0x01, 0x01, 0x02};
        byte[] actual = invokeProcessRtpData(processor, 97, l16Payload);

        assertArrayEquals(new byte[]{0x01, 0x00, 0x02, 0x01}, actual);
    }

    private void assertDecodedG711(int mediaType) throws Exception {
        NettyAsrRtpProcessor processor = new NettyAsrRtpProcessor(mediaType);
        byte[] pcmData = new byte[]{0x00, 0x00, (byte) 0xE8, 0x03, 0x18, (byte) 0xFC, 0x20, 0x00};
        byte[] encodedPayload = AudioCodecUtil.encode(pcmData, mediaType);
        byte[] actual = invokeProcessRtpData(processor, mediaType, encodedPayload);

        assertArrayEquals(AudioCodecUtil.decode(encodedPayload, mediaType), actual);
    }

    private byte[] invokeProcessRtpData(NettyAsrRtpProcessor processor, int payloadType, byte[] payload) throws Exception {
        Method method = NettyAsrRtpProcessor.class.getDeclaredMethod("processRtpData", ByteBuf.class, ByteBufAllocator.class);
        method.setAccessible(true);

        ByteBuf packet = Unpooled.wrappedBuffer(buildRtpPacket(payloadType, payload));
        ByteBuf result = null;
        try {
            result = (ByteBuf) method.invoke(processor, packet, ByteBufAllocator.DEFAULT);
            assertNotNull(result);
            byte[] actual = new byte[result.readableBytes()];
            result.getBytes(result.readerIndex(), actual);
            return actual;
        } finally {
            packet.release();
            if (result != null) {
                result.release();
            }
        }
    }

    private byte[] buildRtpPacket(int payloadType, byte[] payload) {
        byte[] packet = new byte[12 + payload.length];
        packet[0] = (byte) 0x80;
        packet[1] = (byte) (payloadType & 0x7F);
        System.arraycopy(payload, 0, packet, 12, payload.length);
        return packet;
    }
}
