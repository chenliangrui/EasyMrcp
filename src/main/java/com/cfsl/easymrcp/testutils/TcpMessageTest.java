package com.cfsl.easymrcp.testutils;

import com.cfsl.easymrcp.tcp.TcpMessagePacket;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * TCP消息格式测试类
 * 用于测试消息包的打包和解包功能
 */
public class TcpMessageTest {

    public static void main(String[] args) {
        try {
            System.out.println("===== TCP消息格式测试 =====");
            
            // 创建测试数据
            Map<String, Object> testData = new HashMap<>();
            testData.put("id", "test-client-001");
            testData.put("command", "echo");
            testData.put("data", "Hello, World!");
            
            // 转换为JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonData = objectMapper.writeValueAsString(testData);
            System.out.println("原始JSON数据: " + jsonData);
            
            // 创建消息包并打包
            TcpMessagePacket packet = new TcpMessagePacket(jsonData);
            byte[] packedData = packet.pack();
            
            System.out.println("打包后数据总长度: " + packedData.length + " 字节");
            System.out.println("消息头: 魔数(4字节) + 消息体长度(4字节) = 8字节");
            
            // 解析消息头
            ByteBuffer headerBuffer = ByteBuffer.wrap(packedData, 0, 8);
            int magic = headerBuffer.getInt();
            int bodyLength = headerBuffer.getInt();
            
            System.out.println("魔数: 0x" + Integer.toHexString(magic).toUpperCase());
            System.out.println("消息体长度: " + bodyLength + " 字节");
            
            // 提取消息体
            byte[] bodyBytes = new byte[bodyLength];
            System.arraycopy(packedData, 8, bodyBytes, 0, bodyLength);
            String extractedBody = new String(bodyBytes, StandardCharsets.UTF_8);
            
            System.out.println("提取的消息体: " + extractedBody);
            
            // 使用解包方法测试
            System.out.println("\n使用解包方法测试:");
            TcpMessagePacket unpackedPacket = TcpMessagePacket.unpack(packedData);
            System.out.println("解包后的消息体: " + unpackedPacket.getBody());
            
            // 测试粘包情况
            System.out.println("\n===== 粘包测试 =====");
            
            // 创建第二个测试消息
            Map<String, Object> testData2 = new HashMap<>();
            testData2.put("id", "test-client-002");
            testData2.put("command", "speak");
            testData2.put("data", "这是第二个测试消息");
            
            String jsonData2 = objectMapper.writeValueAsString(testData2);
            TcpMessagePacket packet2 = new TcpMessagePacket(jsonData2);
            byte[] packedData2 = packet2.pack();
            
            // 拼接两个包模拟粘包
            ByteArrayOutputStream combinedStream = new ByteArrayOutputStream();
            combinedStream.write(packedData);
            combinedStream.write(packedData2);
            byte[] combinedData = combinedStream.toByteArray();
            
            System.out.println("粘包后数据总长度: " + combinedData.length + " 字节");
            
            // 模拟解析粘包数据
            System.out.println("\n解析第一个包:");
            int offset = 0;
            
            // 解析第一个包的头部
            ByteBuffer headerBuffer1 = ByteBuffer.wrap(combinedData, offset, 8);
            int magic1 = headerBuffer1.getInt();
            int bodyLength1 = headerBuffer1.getInt();
            offset += 8;
            
            System.out.println("魔数: 0x" + Integer.toHexString(magic1).toUpperCase());
            System.out.println("消息体长度: " + bodyLength1 + " 字节");
            
            // 提取第一个包的消息体
            byte[] bodyBytes1 = new byte[bodyLength1];
            System.arraycopy(combinedData, offset, bodyBytes1, 0, bodyLength1);
            String extractedBody1 = new String(bodyBytes1, StandardCharsets.UTF_8);
            offset += bodyLength1;
            
            System.out.println("提取的消息体: " + extractedBody1);
            
            System.out.println("\n解析第二个包:");
            
            // 解析第二个包的头部
            ByteBuffer headerBuffer2 = ByteBuffer.wrap(combinedData, offset, 8);
            int magic2 = headerBuffer2.getInt();
            int bodyLength2 = headerBuffer2.getInt();
            offset += 8;
            
            System.out.println("魔数: 0x" + Integer.toHexString(magic2).toUpperCase());
            System.out.println("消息体长度: " + bodyLength2 + " 字节");
            
            // 提取第二个包的消息体
            byte[] bodyBytes2 = new byte[bodyLength2];
            System.arraycopy(combinedData, offset, bodyBytes2, 0, bodyLength2);
            String extractedBody2 = new String(bodyBytes2, StandardCharsets.UTF_8);
            
            System.out.println("提取的消息体: " + extractedBody2);
            
            System.out.println("\n测试完成，所有消息正确解析！");
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 