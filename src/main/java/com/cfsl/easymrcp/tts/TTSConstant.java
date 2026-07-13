package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

public class TTSConstant {
    public static final byte TTS_END_BYTE = 111;
    public static final byte TTS_INTERRUPT_BYTE = 112;
    public static final byte TTS_SILENCE_BYTE = (byte) 0x00;

    // PCM 帧大小（8kHz, 16bit, 20ms = 320字节）
    public static final int TTS_PCM_FRAME_BYTES = EMConstant.VOIP_L16_BYTES_PER_FRAME;

    // ByteBuf版本常量
    public static final ByteBuf TTS_END_FLAG = Unpooled.unreleasableBuffer(
        ByteBufAllocator.DEFAULT.buffer(2).writeBytes(new byte[] { TTS_END_BYTE, TTS_END_BYTE })
    );
    public static final ByteBuf TTS_INTERRUPT_FLAG = Unpooled.unreleasableBuffer(
        ByteBufAllocator.DEFAULT.buffer(2).writeBytes(new byte[] { TTS_INTERRUPT_BYTE, TTS_INTERRUPT_BYTE })
    );
}
