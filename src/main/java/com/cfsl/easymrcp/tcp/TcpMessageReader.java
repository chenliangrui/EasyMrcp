package com.cfsl.easymrcp.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP消息读取器
 * 用于从输入流中读取和解析TCP消息包，处理粘包和拆包问题
 */
public class TcpMessageReader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpMessageReader.class);
    
    // 输入流
    private final InputStream inputStream;
    
    // 缓冲区，用于存储未完成解析的数据
    private byte[] buffer = new byte[8192]; // 初始8KB缓冲区
    
    // 当前缓冲区中有效数据的长度
    private int dataLength = 0;
    
    /**
     * 创建TCP消息读取器
     * 
     * @param inputStream 输入流
     */
    public TcpMessageReader(InputStream inputStream) {
        this.inputStream = inputStream;
    }
    
    /**
     * 读取所有可用的完整消息
     * 
     * @return 消息列表，每个元素是一个完整的消息体内容
     * @throws IOException 如果读取过程发生IO错误
     */
    public List<String> readMessages() throws IOException {
        List<String> messages = new ArrayList<>();
        
        // 读取可用数据到缓冲区
        int bytesRead = readToBuffer();
        if (bytesRead <= 0 && dataLength == 0) {
            return messages; // 没有数据可读
        }
        
        // 尝试解析多个消息
        while (true) {
            // 如果数据不足以解析头部，则等待更多数据
            if (dataLength < TcpMessagePacket.getHeaderLength()) {
                break;
            }
            
            try {
                // 解析消息体长度
                int bodyLength = TcpMessagePacket.parseBodyLength(buffer);
                
                // 计算整个消息的长度（头部 + 消息体）
                int messageLength = TcpMessagePacket.getHeaderLength() + bodyLength;
                
                // 如果数据不足以解析整个消息，则等待更多数据
                if (dataLength < messageLength) {
                    break;
                }
                
                // 提取完整的消息数据
                byte[] messageData = new byte[messageLength];
                System.arraycopy(buffer, 0, messageData, 0, messageLength);
                
                // 解析消息包
                TcpMessagePacket packet = TcpMessagePacket.unpack(messageData);
                messages.add(packet.getBody());
                
                // 移除已处理的数据
                dataLength -= messageLength;
                if (dataLength > 0) {
                    System.arraycopy(buffer, messageLength, buffer, 0, dataLength);
                }
            } catch (IllegalArgumentException e) {
                // 数据格式错误，尝试恢复
                LOGGER.error("解析消息包失败: {}", e.getMessage());
                
                // 尝试查找下一个魔数
                int nextMagicPos = findNextMagicNumber();
                if (nextMagicPos > 0) {
                    // 移除错误数据直到下一个魔数位置
                    dataLength -= nextMagicPos;
                    System.arraycopy(buffer, nextMagicPos, buffer, 0, dataLength);
                } else {
                    // 没有找到下一个魔数，清空缓冲区
                    dataLength = 0;
                    break;
                }
            }
        }
        
        return messages;
    }
    
    /**
     * 读取可用数据到缓冲区
     * 
     * @return 读取的字节数
     * @throws IOException 如果读取过程发生IO错误
     */
    private int readToBuffer() throws IOException {
        // 确保缓冲区有足够空间
        ensureBufferCapacity();
        
        // 读取数据到缓冲区的剩余空间
        int bytesRead = inputStream.read(buffer, dataLength, buffer.length - dataLength);
        if (bytesRead > 0) {
            dataLength += bytesRead;
        }
        
        return bytesRead;
    }
    
    /**
     * 确保缓冲区有足够的容量
     */
    private void ensureBufferCapacity() {
        // 如果缓冲区已满，则扩容
        if (dataLength == buffer.length) {
            byte[] newBuffer = new byte[buffer.length * 2];
            System.arraycopy(buffer, 0, newBuffer, 0, dataLength);
            buffer = newBuffer;
        }
    }
    
    /**
     * 在缓冲区中查找下一个魔数位置
     * 
     * @return 魔数位置，如果没有找到则返回-1
     */
    private int findNextMagicNumber() {
        // 魔数是4字节，所以至少需要4字节才能查找
        if (dataLength < 4) {
            return -1;
        }
        
        // 从第2个字节开始查找，因为第1个字节已经检查过了
        for (int i = 1; i <= dataLength - 4; i++) {
            // 检查是否匹配魔数
            if ((buffer[i] & 0xFF) == 0x66 &&
                (buffer[i + 1] & 0xFF) == 0xAA &&
                (buffer[i + 2] & 0xFF) == 0xBB &&
                (buffer[i + 3] & 0xFF) == 0x99) {
                return i;
            }
        }
        
        return -1;
    }
}