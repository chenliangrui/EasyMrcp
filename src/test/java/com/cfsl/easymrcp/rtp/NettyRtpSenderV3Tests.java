package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.common.EMConstant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NettyRtpSenderV3Tests {

    @Test
    void sendFrame_shouldAlwaysSwapPayloadAsL16() throws Exception {
        NettyRtpSenderV3 sender = new NettyRtpSenderV3("127.0.0.1", 9010);
        sender.setPayloadType(AudioCodecUtil.PT_PCMA);
        EmbeddedChannel channel = new EmbeddedChannel();
        sender.setRtpChannel(channel);

        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{0x11, 0x22, 0x33, 0x44});
        sender.sendFrame(payload);

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            byte[] actualPayload = new byte[packet.content().readableBytes() - 12];
            packet.content().getBytes(12, actualPayload);
            assertArrayEquals(new byte[]{0x22, 0x11, 0x44, 0x33}, actualPayload);
        } finally {
            packet.release();
            payload.release();
            sender.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void sendSilence_shouldKeepL16FrameSizeAfterPayloadTypeChanges() throws Exception {
        NettyRtpSenderV3 sender = new NettyRtpSenderV3("127.0.0.1", 9011);
        sender.setPayloadType(AudioCodecUtil.PT_PCMU);
        EmbeddedChannel channel = new EmbeddedChannel();
        sender.setRtpChannel(channel);

        sender.sendSilence();

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            assertEquals(12 + EMConstant.VOIP_L16_BYTES_PER_FRAME, packet.content().readableBytes());
        } finally {
            packet.release();
            sender.close();
            channel.finishAndReleaseAll();
        }
    }
}
