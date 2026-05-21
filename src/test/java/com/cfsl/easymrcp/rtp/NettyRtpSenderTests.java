package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.common.EMConstant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NettyRtpSenderTests {

    @Test
    void sendFrame_shouldSwapLittleEndianPayloadForL16() throws Exception {
        NettyRtpSender sender = new NettyRtpSender("127.0.0.1", 9000);
        sender.configureSession(EMConstant.VOIP_L16_BYTES_PER_FRAME, true);
        EmbeddedChannel channel = new EmbeddedChannel();
        sender.setRtpChannel(channel);

        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{0x01, 0x02, 0x03, 0x04});
        sender.sendFrame(payload);

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            byte[] actualPayload = new byte[packet.content().readableBytes() - 12];
            packet.content().getBytes(12, actualPayload);
            assertArrayEquals(new byte[]{0x02, 0x01, 0x04, 0x03}, actualPayload);
        } finally {
            packet.release();
            payload.release();
            sender.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void sendFrame_shouldKeepEncodedPayloadForPcma() throws Exception {
        NettyRtpSender sender = new NettyRtpSender("127.0.0.1", 9001);
        sender.setPayloadType(AudioCodecUtil.PT_PCMA);
        sender.configureSession(EMConstant.VOIP_SAMPLES_PER_FRAME, false);
        EmbeddedChannel channel = new EmbeddedChannel();
        sender.setRtpChannel(channel);

        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{0x11, 0x22, 0x33, 0x44});
        sender.sendFrame(payload);

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            byte[] actualPayload = new byte[packet.content().readableBytes() - 12];
            packet.content().getBytes(12, actualPayload);
            assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44}, actualPayload);
        } finally {
            packet.release();
            payload.release();
            sender.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void sendSilence_shouldMatchCodecFrameSize() throws Exception {
        NettyRtpSender sender = new NettyRtpSender("127.0.0.1", 9002);
        EmbeddedChannel channel = new EmbeddedChannel();
        sender.setRtpChannel(channel);

        sender.configureSession(EMConstant.VOIP_L16_BYTES_PER_FRAME, true);
        sender.sendSilence();
        DatagramPacket l16Packet = channel.readOutbound();
        assertNotNull(l16Packet);
        try {
            assertEquals(12 + EMConstant.VOIP_L16_BYTES_PER_FRAME, l16Packet.content().readableBytes());
        } finally {
            l16Packet.release();
        }

        sender.setPayloadType(AudioCodecUtil.PT_PCMU);
        sender.configureSession(EMConstant.VOIP_SAMPLES_PER_FRAME, false);
        sender.sendSilence();
        DatagramPacket g711Packet = channel.readOutbound();
        assertNotNull(g711Packet);
        try {
            assertEquals(12 + EMConstant.VOIP_SAMPLES_PER_FRAME, g711Packet.content().readableBytes());
        } finally {
            g711Packet.release();
            sender.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void nettyRtpSender_shouldNotExposeLegacyInterruptMethod() {
        boolean hasInterruptMethod = Arrays.stream(NettyRtpSender.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("interrupt") && method.getParameterCount() == 0);

        assertFalse(hasInterruptMethod);
    }
}
