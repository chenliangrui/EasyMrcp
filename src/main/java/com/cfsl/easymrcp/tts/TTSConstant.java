package com.cfsl.easymrcp.tts;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

public class TTSConstant {
    public static final byte TTS_END_BYTE = 111;
    public static final byte TTS_INTERRUPT_BYTE = 112;
    public static final byte TTS_SILENCE_BYTE = (byte) 0xd5;
    
    // ByteBuf版本常量
    public static final ByteBuf TTS_END_FLAG = Unpooled.unreleasableBuffer(
        ByteBufAllocator.DEFAULT.buffer(2).writeBytes(new byte[] { TTS_END_BYTE, TTS_END_BYTE })
    );
    public static final ByteBuf TTS_INTERRUPT_FLAG = Unpooled.unreleasableBuffer(
        ByteBufAllocator.DEFAULT.buffer(2).writeBytes(new byte[] { TTS_INTERRUPT_BYTE, TTS_INTERRUPT_BYTE })
    );
}
