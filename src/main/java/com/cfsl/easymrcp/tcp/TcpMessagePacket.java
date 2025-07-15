package com.cfsl.easymrcp.tcp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * TCP消息包类
 * 用于处理TCP粘包问题的消息结构，包含消息头和消息体
 * 
 * 消息格式：
 * +-------------------+------------------------+
 * | 消息头(8字节)      | 消息体(变长)           |
 * +-------------------+------------------------+
 * | 魔数(4字节) | 长度(4字节) | JSON数据        |
 * +-------------------+------------------------+
 */
public class TcpMessagePacket {
    
    // 魔数，用于标识消息的开始，固定为 0x66AABB99
    private static final int MAGIC_NUMBER = 0x66AABB99;
    
    // 消息头长度为8字节：4字节魔数 + 4字节消息体长度
    private static final int HEADER_LENGTH = 8;
    
    // 消息体内容
    private String body;

    /**
     * 创建TCP消息包
     * 
     * @param body 消息体内容
     */
    public TcpMessagePacket(String body) {
        this.body = body;
    }

    /**
     * 获取消息体内容
     * 
     * @return 消息体内容
     */
    public String getBody() {
        return body;
    }

    /**
     * 将消息打包成字节数组
     * 
     * @return 打包后的字节数组
     */
    public byte[] pack() {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        int bodyLength = bodyBytes.length;
        
        // 创建包含头部和消息体的缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + bodyLength);
        
        // 写入魔数
        buffer.putInt(MAGIC_NUMBER);
        
        // 写入消息体长度
        buffer.putInt(bodyLength);
        
        // 写入消息体
        buffer.put(bodyBytes);
        
        return buffer.array();
    }

    /**
     * 从字节数组解析消息包
     * 
     * @param data 完整的消息包字节数组
     * @return 解析后的消息包对象
     * @throws IllegalArgumentException 如果数据格式不正确
     */
    public static TcpMessagePacket unpack(byte[] data) {
        if (data.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("数据长度不足，无法解析消息头");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        // 读取并验证魔数
        int magic = buffer.getInt();
        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException("魔数不匹配，数据可能已损坏");
        }
        
        // 读取消息体长度
        int bodyLength = buffer.getInt();
        
        // 检查消息体长度
        if (data.length != HEADER_LENGTH + bodyLength) {
            throw new IllegalArgumentException("消息体长度与实际不符");
        }
        
        // 读取消息体
        byte[] bodyBytes = new byte[bodyLength];
        buffer.get(bodyBytes);
        
        // 创建消息包对象
        return new TcpMessagePacket(new String(bodyBytes, StandardCharsets.UTF_8));
    }

    /**
     * 获取消息头长度
     * 
     * @return 消息头长度(字节)
     */
    public static int getHeaderLength() {
        return HEADER_LENGTH;
    }

    /**
     * 从数据中解析消息体长度
     * 
     * @param headerData 消息头数据(8字节)
     * @return 消息体长度
     * @throws IllegalArgumentException 如果数据格式不正确
     */
    public static int parseBodyLength(byte[] headerData) {
        if (headerData.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("消息头数据长度不足");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(headerData);
        
        // 读取并验证魔数
        int magic = buffer.getInt();
        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException("魔数不匹配，数据可能已损坏");
        }
        
        // 读取消息体长度
        return buffer.getInt();
    }
} 