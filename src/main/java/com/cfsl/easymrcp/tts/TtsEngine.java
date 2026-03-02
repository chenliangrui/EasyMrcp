package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TtsEngine {
    // 用于预加载查询id
    @Getter
    @Setter
    private String id;
    @Setter
    protected TtsHandler ttsHandler;
    @Setter
    protected String voice;
    // TTS预加载缓存
    @Setter
    private NettyAudioRingBuffer nettyAudioRingBuffer;
    // 本次tts的版本
    @Setter
    private int ttsVersion = 0;
    // 首包跳过标志
    protected boolean isFirstPacket = true;
    // 首包跳过字节计数
    @Setter
    protected int skipBytesInTheFirstPacket;

    public abstract void create();

    public abstract void speak(String text);

    /**
     * 关闭TTS资源
     */
    public abstract void ttsClose();

    /**
     * byte[]形式写入TTS音频数据
     * @param audioChunk    TTS音频数据
     * @param bytesRead     音频数据大小
     */
    protected void putAudioData(byte[] audioChunk, int bytesRead) {
        if (ttsVersion == ttsHandler.getTtsVersion() && nettyAudioRingBuffer == null) {
            log.debug("{}, 播放", ttsVersion);
            this.ttsHandler.putAudioData(audioChunk, bytesRead);
        } else if (ttsVersion > ttsHandler.getTtsVersion() || nettyAudioRingBuffer != null) {
            log.debug("{}, 预加载", ttsVersion);
            preloadData(audioChunk, bytesRead);
        } else if (ttsVersion < ttsHandler.getTtsVersion()) return;
    }

    /**
     * ByteBuf形式写入结束标志
     * @param byteBuf 结束标志
     */
    protected void putAudioData(ByteBuf byteBuf) {
        if (ttsVersion == ttsHandler.getTtsVersion() && nettyAudioRingBuffer == null) {
            log.debug("{}, 播放停止位", ttsVersion);
            this.ttsHandler.putAudioData(byteBuf);
        } else if (ttsVersion > ttsHandler.getTtsVersion() || nettyAudioRingBuffer != null) {
            log.debug("{}, 预加载停止位", ttsVersion);
            preloadData(byteBuf);
        } else if (ttsVersion < ttsHandler.getTtsVersion()) return;
    }

    /**
     * 跳过首包中最开始的n毫秒数据
     * @param audioChunk 音频数据
     */
    protected void putAudioDataWithSkip(byte[] audioChunk, int bytesRead) {
        // 处理首包，跳过前n毫秒
        if (isFirstPacket && skipBytesInTheFirstPacket != 0) {
            isFirstPacket = false;
            if (audioChunk.length > skipBytesInTheFirstPacket) {
                // 首包大于n毫秒，跳过前skip_bytes字节
                byte[] trimmedBytes = new byte[audioChunk.length - skipBytesInTheFirstPacket];
                System.arraycopy(audioChunk, skipBytesInTheFirstPacket, trimmedBytes, 0, trimmedBytes.length);
                putAudioData(trimmedBytes, trimmedBytes.length);
                log.debug("{}首包已跳过{}字节，剩余大小：{}", getId(), skipBytesInTheFirstPacket, trimmedBytes.length);
            } else {
                // 首包小于等于n毫秒，直接丢弃
                log.debug("{}首包大小{}字节，已完全跳过", getId(), audioChunk.length);
            }
        } else {
            putAudioData(audioChunk, bytesRead);
        }
    }

    /**
     * 预加载情况
     * 缓存预加载的音频数据
     * @param audioChunk    TTS音频数据
     * @param bytesRead     音频数据大小
     */
    private void preloadData(byte[] audioChunk, int bytesRead) {
        if (audioChunk == null || audioChunk.length == 0) {
            return;
        }
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(bytesRead);
        byteBuf.writeBytes(audioChunk, 0 , bytesRead);
        nettyAudioRingBuffer.write(byteBuf);
        byteBuf.release();
    }

    /**
     * 预加载情况
     * 写入结束标志
     * @param byteBuf   结束标志
     */
    private void preloadData(ByteBuf byteBuf) {
        nettyAudioRingBuffer.write(byteBuf);
        byteBuf.release();
    }

    /**
     * 播放预加载音频
     * 等待缓冲中有数据后再读取，运行完后自动清空缓存音频数据
     */
    public void playPreLoadData() {
        try {
            // 循环读取nettyAudioRingBuffer直到遇到TTS_END_FLAG结束标志
            while (true) {
                // 如果缓冲区为空，短暂等待
                if (nettyAudioRingBuffer.isEmpty()) {
                    Thread.sleep(100);
                    continue;
                }
                
                // 读取所有可用数据
                ByteBuf audioData = nettyAudioRingBuffer.readAll();
                if (audioData != null && audioData.readableBytes() > 0) {
                    // 检查最后两个字节是否为结束标志
                    boolean hasEndFlag = false;
                    if (audioData.readableBytes() >= 2) {
                        int lastIndex = audioData.writerIndex() - 1;
                        byte lastByte = audioData.getByte(lastIndex);
                        byte secondLastByte = audioData.getByte(lastIndex - 1);
                        
                        if (secondLastByte == TTSConstant.TTS_END_BYTE && lastByte == TTSConstant.TTS_END_BYTE) {
                            hasEndFlag = true;
                        }
                    }
                    
                    // 发送数据（包含结束标志）
                    this.ttsHandler.putAudioData(audioData);
                    audioData.release(); // 释放ByteBuf
                    
                    // 如果检测到结束标志，清空缓冲区并退出循环
                    if (hasEndFlag) {
                        nettyAudioRingBuffer.clear();
                        nettyAudioRingBuffer.release();
                        nettyAudioRingBuffer = null;
                        break;
                    }
                } else if (audioData != null) {
                    audioData.release();
                }
            }
        } catch (Exception e) {
            log.error("播放预加载数据时发生错误: {}", e.getMessage(), e);
        }
    }
}
