package com.cfsl.easymrcp.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Netty消息解码器
 * 将字节流解码为消息对象，处理TCP粘包/拆包问题
 */
@Slf4j
public class NettyMessageDecoder extends ByteToMessageDecoder {
    
    // 魔数，用于标识消息的开始，固定为 0x66AABB99
    private static final int MAGIC_NUMBER = 0x66AABB99;
    
    // 消息头长度为8字节：4字节魔数 + 4字节消息体长度
    private static final int HEADER_LENGTH = 8;
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 标记当前读取位置
        in.markReaderIndex();
        
        // 如果数据不足以解析头部，则等待更多数据
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }
        
        // 读取魔数
        int magic = in.readInt();
        
        // 验证魔数
        if (magic != MAGIC_NUMBER) {
            log.error("魔数不匹配: 0x{}, 期望: 0x{}", Integer.toHexString(magic), Integer.toHexString(MAGIC_NUMBER));
            
            // 魔数不匹配，跳过一个字节，继续查找
            in.resetReaderIndex();
            in.skipBytes(1);
            return;
        }
        
        // 读取消息体长度
        int bodyLength = in.readInt();
        
        // 长度校验
        if (bodyLength < 0 || bodyLength > 10 * 1024 * 1024) { // 限制10MB
            log.error("消息体长度不合法: {}", bodyLength);
            ctx.close(); // 关闭连接
            return;
        }
        
        // 如果数据不足以解析消息体，则重置读取位置，等待更多数据
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }
        
        // 读取消息体
        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);
        
        // 解析为字符串
        String messageContent = new String(bodyBytes, StandardCharsets.UTF_8);
        
        // 将解析后的消息传递给下一个处理器
        out.add(messageContent);
    }
} 