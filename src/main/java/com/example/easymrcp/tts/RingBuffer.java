package com.example.easymrcp.tts;

/**
 * TODO 解决RingBuffer覆盖问题
 */
class RingBuffer {
    private final byte[] buffer;
    private final int capacity;
    private int writePos;
    private int readPos;
    private int available;

    public RingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new byte[capacity];
        this.writePos = 0;
        this.readPos = 0;
        this.available = 0;
    }

    public synchronized void put(byte[] data) {
        int bytesToWrite = Math.min(data.length, capacity - available);
        if (bytesToWrite <= 0) return;

        // 写入到缓冲区尾部
        int firstPart = Math.min(bytesToWrite, capacity - writePos);
        System.arraycopy(data, 0, buffer, writePos, firstPart);

        // 处理回绕写入
        if (bytesToWrite > firstPart) {
            System.arraycopy(data, firstPart, buffer, 0, bytesToWrite - firstPart);
        }

        writePos = (writePos + bytesToWrite) % capacity;
        available += bytesToWrite;
    }

    public synchronized byte[] take(int maxLength) {
        if (available <= 0) return null;

        int bytesToRead = Math.min(maxLength, available);
        byte[] result = new byte[bytesToRead];

        // 从缓冲区头部读取
        int firstPart = Math.min(bytesToRead, capacity - readPos);
        System.arraycopy(buffer, readPos, result, 0, firstPart);

        // 处理回绕读取
        if (bytesToRead > firstPart) {
            System.arraycopy(buffer, 0, result, firstPart, bytesToRead - firstPart);
        }

        readPos = (readPos + bytesToRead) % capacity;
        available -= bytesToRead;
        return result;
    }

    public int getAvailable() {
        return available;
    }
}
