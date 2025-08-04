package com.cfsl.easymrcp.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * Netty消息编码器
 * 将消息对象编码为字节流，添加魔数和长度信息
 */
@Slf4j
public class NettyMessageEncoder extends MessageToByteEncoder<String> {
    
    // 魔数，用于标识消息的开始，固定为 0x66AABB99
    private static final int MAGIC_NUMBER = 0x66AABB99;
    
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        // 将消息转换为字节数组
        byte[] bodyBytes = msg.getBytes(StandardCharsets.UTF_8);
        int bodyLength = bodyBytes.length;
        
        // 写入魔数
        out.writeInt(MAGIC_NUMBER);
        
        // 写入消息体长度
        out.writeInt(bodyLength);
        
        // 写入消息体
        out.writeBytes(bodyBytes);
    }
} 