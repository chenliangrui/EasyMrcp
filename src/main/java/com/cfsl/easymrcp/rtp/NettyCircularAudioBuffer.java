package com.cfsl.easymrcp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于单个ByteBuf的环形缓冲区
 * 使用读写指针控制，简洁高效的经典环形缓冲区实现
 * 
 * <p>设计原理：</p>
 * <ul>
 *   <li>单个连续内存块，缓存友好</li>
 *   <li>读写指针环形移动</li>
 *   <li>固定3秒音频数据容量</li>
 *   <li>容量满时自动覆盖最旧数据</li>
 *   <li>O(1)复杂度的指针操作</li>
 * </ul>
 * 
 * <p>线程安全：</p>
 * <ul>
 *   <li>设计为单线程使用（Netty EventLoop线程）</li>
 *   <li>无需同步锁，性能更优</li>
 *   <li>依赖Netty的串行处理保证</li>
 * </ul>
 * 
 * @author EasyMrcp
 */
@Slf4j
public class NettyCircularAudioBuffer {
    
    /** ByteBuf分配器 */
    private final ByteBufAllocator allocator;
    
    /** 环形缓冲区 */
    private ByteBuf circularBuffer;
    
    /** 缓冲区固定容量（字节） - 固定3秒音频数据 */
    private final int capacity;
    
    /** 采样率 */
    private final int sampleRate;
    
    /** 写指针位置 */
    private int writePos = 0;
    
    /** 读指针位置 */
    private int readPos = 0;
    
    /** 当前有效数据大小 */
    private int dataSize = 0;
    
    /** 缓冲区是否已关闭 */
    private boolean closed = false;

    /**
     * 构造函数 - 固定3秒缓冲容量
     * 
     * @param allocator ByteBuf分配器
     * @param sampleRate 采样率（Hz）
     */
    public NettyCircularAudioBuffer(ByteBufAllocator allocator, int sampleRate) {
        this.allocator = allocator;
        this.sampleRate = sampleRate;
        this.capacity = sampleRate * 3 * 2; // 固定3秒音频数据
        this.circularBuffer = allocator.buffer(capacity);
        
        log.debug("创建环形缓冲区 - 采样率: {}Hz, 固定容量: 3秒, {}字节", 
                sampleRate, capacity);
    }
    
    /**
     * 写入音频数据，固定3秒容量，满时覆盖最旧数据
     * 注意：设计为单线程使用（EventLoop线程），无需同步锁
     */
    public void write(ByteBuf data) {
        checkNotClosed();
        
        if (data == null || !data.isReadable()) {
            return;
        }
        
        int dataLength = data.readableBytes();
        
        // 如果写入后超过容量，覆盖最旧的数据
        if (dataSize + dataLength > capacity) {
            int overflow = dataSize + dataLength - capacity;
            moveReadPointer(overflow);
            dataSize -= overflow;
        }
        
        // 写入数据（可能需要环形写入）
        writeToCircularBuffer(data);
        dataSize += dataLength;
        
        log.trace("写入音频: {}字节, 缓冲区: {}/{}", dataLength, dataSize, capacity);
    }
    
    /**
     * 读取指定长度的数据
     */
    public ByteBuf read(int length) {
        checkNotClosed();
        
        if (length <= 0 || dataSize == 0) {
            return allocator.buffer(0);
        }
        
        int actualLength = Math.min(length, dataSize);
        ByteBuf result = allocator.buffer(actualLength);
        
        // 从环形缓冲区读取数据
        readFromCircularBuffer(result, actualLength);
        dataSize -= actualLength;
        
        return result;
    }
    
    /**
     * 读取所有数据
     */
    public ByteBuf readAll() {
        return read(dataSize);
    }
    
    /**
     * 将读指针向前移动指定时间
     */
    public void moveReadPointerBack(int milliseconds) {
        checkNotClosed();
        
        if (dataSize == 0) {
            return;
        }
        
        // 计算需要保留的字节数
        int bytesToKeep = Math.min(sampleRate * milliseconds * 2 / 1000, dataSize);
        
        // 移动读指针，丢弃超出范围的数据
        int bytesToDiscard = dataSize - bytesToKeep;
        if (bytesToDiscard > 0) {
            moveReadPointer(bytesToDiscard);
            dataSize = bytesToKeep;
        }
        
        log.info("移动读指针到{}ms前，保留数据: {}字节", milliseconds, dataSize);
    }
    
    /**
     * 清空缓冲区
     */
    public void clear() {
        checkNotClosed();
        writePos = 0;
        readPos = 0;
        dataSize = 0;
        log.debug("清空环形缓冲区");
    }
    
    /**
     * 获取当前数据大小
     */
    public int getSize() {
        return dataSize;
    }
    
    /**
     * 获取容量
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return dataSize == 0;
    }
    
    /**
     * 获取使用率
     */
    public double getUsageRatio() {
        return capacity > 0 ? (double) dataSize / capacity : 0.0;
    }
    
    /**
     * 检查是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * 释放资源
     */
    public synchronized void release() {
        if (closed) {
            return;
        }
        
        if (circularBuffer != null) {
            circularBuffer.release();
        }
        closed = true;
        log.debug("释放环形缓冲区资源");
    }
    

    /**
     * 写入数据到环形缓冲区
     */
    private void writeToCircularBuffer(ByteBuf data) {
        int remaining = data.readableBytes();
        int sourceIndex = data.readerIndex();
        
        while (remaining > 0) {
            int spaceToEnd = capacity - writePos;
            int bytesToWrite = Math.min(remaining, spaceToEnd);
            
            // 写入数据
            circularBuffer.setBytes(writePos, data, sourceIndex, bytesToWrite);
            
            writePos = (writePos + bytesToWrite) % capacity;
            sourceIndex += bytesToWrite;
            remaining -= bytesToWrite;
        }
    }
    
    /**
     * 从环形缓冲区读取数据
     */
    private void readFromCircularBuffer(ByteBuf dest, int length) {
        int remaining = length;
        
        while (remaining > 0) {
            int dataToEnd = capacity - readPos;
            int bytesToRead = Math.min(remaining, dataToEnd);
            
            // 读取数据
            dest.writeBytes(circularBuffer, readPos, bytesToRead);
            
            readPos = (readPos + bytesToRead) % capacity;
            remaining -= bytesToRead;
        }
    }
    
    /**
     * 移动读指针
     */
    private void moveReadPointer(int bytes) {
        readPos = (readPos + bytes) % capacity;
    }
    
    /**
     * 检查是否未关闭
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("环形缓冲区已关闭");
        }
    }
    
    /**
     * 获取状态信息
     */
    public String getStatusInfo() {
        return String.format("CircularAudioBuffer{size=%d/%d bytes, usage=%.1f%%, closed=%s}",
                dataSize, capacity, getUsageRatio() * 100, closed);
    }
    
    @Override
    public String toString() {
        return getStatusInfo();
    }
} 