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
public class NettyAudioRingBuffer {
    
    /** ByteBuf分配器 */
    private final ByteBufAllocator allocator;
    
    /** 环形缓冲区 */
    private ByteBuf circularBuffer;
    
    /** 缓冲区当前容量（字节） */
    private int capacity;
    
    /** 采样率 */
    private final int sampleRate;
    
    /** 缓冲时长（秒） */
    private int bufferSeconds;
    
    /** 是否启用自动扩容（TTS模式） */
    private final boolean enableAutoExpand;
    
    /** 写指针位置 */
    private int writePos = 0;
    
    /** 读指针位置 */
    private int readPos = 0;
    
    /** 当前有效数据大小 */
    private int dataSize = 0;
    
    /** 缓冲区是否已关闭 */
    private boolean closed = false;

    /**
     * 构造函数 - 默认3秒缓冲容量（ASR模式，不扩容）
     * 
     * @param allocator ByteBuf分配器
     * @param sampleRate 采样率（Hz）
     */
    public NettyAudioRingBuffer(ByteBufAllocator allocator, int sampleRate) {
        this(allocator, sampleRate, 3, false); // 默认3秒，ASR模式
    }
    
    /**
     * 构造函数 - 自定义缓冲容量和扩容模式
     * 
     * @param allocator ByteBuf分配器
     * @param sampleRate 采样率（Hz）
     * @param bufferSeconds 缓冲时长（秒）
     * @param enableAutoExpand 是否启用自动扩容（TTS模式）
     */
    public NettyAudioRingBuffer(ByteBufAllocator allocator, int sampleRate, int bufferSeconds, boolean enableAutoExpand) {
        this.allocator = allocator;
        this.sampleRate = sampleRate;
        this.bufferSeconds = Math.max(1, bufferSeconds); // 最少1秒
        this.enableAutoExpand = enableAutoExpand;
        this.capacity = calculateCapacity(sampleRate, this.bufferSeconds);
        this.circularBuffer = allocator.buffer(capacity);
        
        String mode = enableAutoExpand ? "TTS模式(支持扩容)" : "ASR模式(固定容量)";
        log.debug("创建环形缓冲区 - 采样率: {}Hz, 缓冲时长: {}秒, 容量: {}字节({:.1f}MB), {}", 
                sampleRate, this.bufferSeconds, capacity, capacity / 1024.0 / 1024.0, mode);
    }
    
    /**
     * 构造函数 - 直接按字节数分配容量（用于VAD等特殊场景）
     * 
     * @param allocator ByteBuf分配器
     * @param capacityBytes 直接指定的缓冲区容量（字节）
     * @param enableAutoExpand 是否启用自动扩容
     */
    public NettyAudioRingBuffer(ByteBufAllocator allocator, int capacityBytes, boolean enableAutoExpand) {
        this.allocator = allocator;
        this.sampleRate = 16000; // 默认采样率，仅用于日志显示
        this.capacity = Math.max(1024, capacityBytes); // 最少1KB
        this.bufferSeconds = capacity / (sampleRate * 2); // 计算等效时长
        this.enableAutoExpand = enableAutoExpand;
        this.circularBuffer = allocator.buffer(capacity);
        
        String mode = enableAutoExpand ? "TTS模式(支持扩容)" : "ASR模式(固定容量)";
        log.debug("创建环形缓冲区(按字节) - 容量: {}字节({:.1f}KB), 等效时长: {}秒, {}", 
                capacity, capacity / 1024.0, bufferSeconds, mode);
    }
    
    /**
     * 计算缓冲区容量
     * 
     * @param sampleRate 采样率
     * @param seconds 缓冲秒数
     * @return 容量（字节）
     */
    private static int calculateCapacity(int sampleRate, int seconds) {
        return sampleRate * seconds * 2; // 16位PCM，每个样本2字节
    }
    
    /**
     * 尝试扩容缓冲区到原来时间的两倍
     * 
     * @param additionalSize 需要额外的字节数
     * @return 是否扩容成功
     */
    private boolean tryExpand(int additionalSize) {
        // 计算新的缓冲时长（两倍）
        int newBufferSeconds = bufferSeconds * 2;
        int newCapacity = calculateCapacity(sampleRate, newBufferSeconds);
        
        // 检查是否还需要更多容量
        if (dataSize + additionalSize > newCapacity) {
            // 计算实际需要的秒数
            int requiredBytes = dataSize + additionalSize;
            int requiredSeconds = (requiredBytes / (sampleRate * 2)) + 1;
            newBufferSeconds = Math.max(newBufferSeconds, requiredSeconds);
            newCapacity = calculateCapacity(sampleRate, newBufferSeconds);
        }
        
        try {
            // 创建新的更大的缓冲区
            ByteBuf newBuffer = allocator.buffer(newCapacity);
            
            // 复制现有数据到新缓冲区
            if (dataSize > 0) {
                copyDataToNewBuffer(newBuffer);
            }
            
            // 释放旧缓冲区
            circularBuffer.release();
            
            // 更新状态
            circularBuffer = newBuffer;
            int oldCapacity = capacity;
            capacity = newCapacity;
            bufferSeconds = newBufferSeconds;
            
            // 重置指针（新缓冲区中数据是线性排列的）
            readPos = 0;
            writePos = dataSize;
            
            log.info("TTS模式：缓冲区扩容成功 - 从{}秒({:.1f}MB)扩容到{}秒({:.1f}MB)", 
                    oldCapacity / (sampleRate * 2), oldCapacity / 1024.0 / 1024.0,
                    bufferSeconds, capacity / 1024.0 / 1024.0);
            
            return true;
            
        } catch (Exception e) {
            log.warn("TTS模式：缓冲区扩容失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 将现有数据复制到新缓冲区
     */
    private void copyDataToNewBuffer(ByteBuf newBuffer) {
        if (dataSize == 0) {
            return;
        }
        
        // 按顺序读取所有现有数据并写入新缓冲区
        int remaining = dataSize;
        int currentReadPos = readPos;
        
        while (remaining > 0) {
            int dataToEnd = capacity - currentReadPos;
            int bytesToCopy = Math.min(remaining, dataToEnd);
            
            // 从旧缓冲区复制到新缓冲区
            newBuffer.writeBytes(circularBuffer, currentReadPos, bytesToCopy);
            
            currentReadPos = (currentReadPos + bytesToCopy) % capacity;
            remaining -= bytesToCopy;
        }
    }
    
    /**
     * 写入音频数据，支持自动扩容，满时可选择覆盖或扩容
     * 注意：设计为单线程使用（EventLoop线程），无需同步锁
     */
    public void write(ByteBuf data) {
        if (checkClosed()) {
            return;
        }
        
        if (data == null || !data.isReadable()) {
            return;
        }
        
        int dataLength = data.readableBytes();
        
        // 检查是否需要扩容（仅在TTS模式下）
        if (dataSize + dataLength > capacity) {
            if (enableAutoExpand) {
                // TTS模式：尝试扩容，如果扩容失败则覆盖旧数据
                if (!tryExpand(dataLength)) {
                    int overflow = dataSize + dataLength - capacity;
                    moveReadPointer(overflow);
                    dataSize -= overflow;
                    log.debug("TTS模式：扩容失败，覆盖旧数据: {}字节", overflow);
                }
            } else {
                // ASR模式：直接覆盖旧数据，不扩容
                int overflow = dataSize + dataLength - capacity;
                moveReadPointer(overflow);
                dataSize -= overflow;
                log.trace("ASR模式：容量不足，覆盖旧数据: {}字节", overflow);
            }
        }
        
        // 写入数据（可能需要环形写入）
        writeToCircularBuffer(data);
        dataSize += dataLength;
        
        log.trace("写入音频: {}字节, 缓冲区: {}/{} ({:.1f}%)", 
                dataLength, dataSize, capacity, (double) dataSize / capacity * 100);
    }
    
    /**
     * 读取指定长度的数据
     */
    public ByteBuf read(int length) {
        if (checkClosed()) {
            return allocator.buffer(0);
        }
        
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
        if (checkClosed()) {
            return;
        }
        
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
        
        log.debug("移动读指针到{}ms前，保留数据: {}字节", milliseconds, dataSize);
    }
    
    /**
     * 清空缓冲区
     */
    public void clear() {
        if (checkClosed()) {
            return;
        }
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
     * 获取缓冲时长（秒）
     */
    public int getBufferSeconds() {
        return bufferSeconds;
    }
    
    /**
     * 获取当前已使用的时长（秒）
     */
    public double getUsedSeconds() {
        return (double) dataSize / (sampleRate * 2);
    }
    
    /**
     * 获取剩余缓冲时长（秒）
     */
    public double getRemainingSeconds() {
        return (double) (capacity - dataSize) / (sampleRate * 2);
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
     * 检查是否已关闭
     * @return true-已关闭，false-未关闭可用
     */
    private boolean checkClosed() {
        return closed;
    }
    
    /**
     * 获取状态信息
     */
    public String getStatusInfo() {
        String mode = enableAutoExpand ? "TTS" : "ASR";
        return String.format("CircularAudioBuffer[%s]{size=%d/%d bytes(%.1fs/%.1fs), usage=%.1f%%, sampleRate=%dHz, closed=%s}",
                mode, dataSize, capacity, getUsedSeconds(), (double)bufferSeconds, 
                getUsageRatio() * 100, sampleRate, closed);
    }
    
    /**
     * 预览数据但不移除（用于TTS处理中的peek需求）
     * 
     * @param maxLength 最大预览长度
     * @return 预览的ByteBuf，如果没有数据则返回null
     */
    public ByteBuf peek(int maxLength) {
        if (closed) {
            return null;
        }

        if (maxLength <= 0 || dataSize == 0) {
            return null;
        }
        
        int actualLength = Math.min(maxLength, dataSize);
        ByteBuf result = allocator.buffer(actualLength);
        
        // 保存当前读指针位置
        int savedReadPos = readPos;
        
        // 读取数据但不更新dataSize和readPos
        peekFromCircularBufferToByteBuf(result, actualLength);
        
        // 恢复读指针位置
        readPos = savedReadPos;
        
        return result;
    }
    
    /**
     * 从环形缓冲区预览数据到ByteBuf（不移动读指针）
     */
    private void peekFromCircularBufferToByteBuf(ByteBuf dest, int length) {
        int remaining = length;
        int tempReadPos = readPos;
        
        while (remaining > 0) {
            int dataToEnd = capacity - tempReadPos;
            int bytesToRead = Math.min(remaining, dataToEnd);
            
            // 直接读取到ByteBuf
            dest.writeBytes(circularBuffer, tempReadPos, bytesToRead);
            
            tempReadPos = (tempReadPos + bytesToRead) % capacity;
            remaining -= bytesToRead;
        }
    }
    
    @Override
    public String toString() {
        return getStatusInfo();
    }
} 