package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.G711AUtil;
import com.cfsl.easymrcp.rtp.NettyCircularAudioBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSender;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTS音频处理器
 * 负责处理和发送TTS合成的音频数据
 * 使用NettyCircularAudioBuffer提供高性能的环形缓冲区
 */
@Slf4j
public class NettyTtsRtpProcessor {
    private NettyRtpSender sender;
    @Setter
    private TtsCallback callback;
    private String reSample;
    
    // 缓冲区配置：支持大容量TTS音频数据
    private static final int TTS_INPUT_BUFFER_SECONDS = 30;  // 输入缓冲区
    private static final int TTS_OUTPUT_BUFFER_SECONDS = 30; // 输出缓冲区
    private static final int SAMPLE_RATE = EMConstant.VOIP_SAMPLE_RATE; // 8kHz
    
    // 线程间缓冲队列 - 使用NettyCircularAudioBuffer替代RingBuffer
    private final NettyCircularAudioBuffer inputRingBuffer;
    private final NettyCircularAudioBuffer outputRingBuffer;
    
    private final AtomicBoolean stop = new AtomicBoolean(false);
    
    // 为解决24khz采样率重采样到8kHz采样率时数据长度不为6的倍数时导致的偶发的噪声问题，统一读取6 * n字节
    private int receiveTakeBytes = 6 * 500;
    
    /**
     * 构造函数 - 使用RtpManager
     *
     * @param reSample       重采样配置
     * @param remoteIp       远程IP地址
     * @param remotePort     远程端口
     */
    public NettyTtsRtpProcessor(String reSample, String remoteIp, int remotePort) {
        this.reSample = reSample;
        
        // 初始化高性能环形缓冲区，TTS模式支持自动扩容
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
        this.inputRingBuffer = new NettyCircularAudioBuffer(allocator, SAMPLE_RATE, TTS_INPUT_BUFFER_SECONDS, true);  // TTS模式
        this.outputRingBuffer = new NettyCircularAudioBuffer(allocator, SAMPLE_RATE, TTS_OUTPUT_BUFFER_SECONDS, true); // TTS模式
        
        log.info("TTS缓冲区初始化完成");
        
        try {
            // 创建RTP发送器
            this.sender = new NettyRtpSender(remoteIp, remotePort);
        } catch (Exception e) {
            log.error("初始化NettyTtsRtpProcessor失败", e);
        }
    }

    public void setRtpChannel(Channel channel) {
        sender.setRtpChannel(channel);
    }
    
    /**
     * 开始处理音频数据
     */
    public void startProcessing() {
        new Thread(() -> {
            while (true) {
                try {
                    // 为解决24khz采样率重采样到8kHz采样率时数据长度不为6的倍数时导致的偶发的噪声问题，统一读取6 * n字节
                    while (inputRingBuffer.getSize() == 0 || inputRingBuffer.getSize() < receiveTakeBytes) {
                        // 判断是否含有结束标志，有的话取出最后的音频数据
                        if (inputRingBuffer.getSize() > 0) {
                            byte[] peek = inputRingBuffer.peek(receiveTakeBytes);
                            if (peek != null && peek.length >= 2 && 
                                peek[peek.length - 2] == TTSConstant.TTS_END_BYTE && peek[peek.length - 1] == TTSConstant.TTS_END_BYTE) {
                                break;
                            }
                        }
                        Thread.sleep(200);
                        if (stop.get()) {
                            log.info("音频处理线程已停止");
                            return;
                        }
                    }
                    
                    // 从输入缓冲区读取数据
                    byte[] pcmData = takeData(inputRingBuffer, receiveTakeBytes);
                    byte[] processedData;
                    if (reSample != null && reSample.equals("downsample24kTo8k")) {
                        processedData = downsample24kTo8k(pcmData);
                    } else {
                        processedData = pcmData;
                    }
                    byte[] g711Data = G711AUtil.encode(processedData);
                    putData(outputRingBuffer, g711Data);
                    if (pcmData != null && pcmData.length >= 2 && 
                        pcmData[pcmData.length - 2] == TTSConstant.TTS_END_BYTE && pcmData[pcmData.length - 1] == TTSConstant.TTS_END_BYTE) {
                        // 结束标记
                        putData(outputRingBuffer, TTSConstant.TTS_END_FLAG);
                    }
                } catch (Exception e) {
                    log.error("处理音频数据异常", e);
                }
            }
        }).start();
    }
    
    /**
     * 将PCM字节流从24kHz降采样到8kHz
     */
    public static byte[] downsample24kTo8k(byte[] inputBytes) {
        int sampleSize = 2; // 每个采样点16-bit，即2字节
        int ratio = 3;      // 24kHz -> 8kHz，每3个采样点降为1个
        int totalSamples = inputBytes.length / sampleSize;
        int newSamples = totalSamples / ratio;

        byte[] outputBytes = new byte[newSamples * sampleSize];

        for (int i = 0; i < newSamples; i++) {
            int idx1 = i * ratio * sampleSize;
            int idx2 = idx1 + sampleSize;
            int idx3 = idx2 + sampleSize;

            // 防止越界（尾部可能不足3个采样点）
            if (idx3 + 1 >= inputBytes.length) {
                break;
            }

            // 读取3个采样值（每个采样是2字节，小端）
            int s1 = (inputBytes[idx1 + 1] << 8) | (inputBytes[idx1] & 0xFF);
            int s2 = (inputBytes[idx2 + 1] << 8) | (inputBytes[idx2] & 0xFF);
            int s3 = (inputBytes[idx3 + 1] << 8) | (inputBytes[idx3] & 0xFF);

            int avg = (s1 + s2 + s3) / 3;

            // 限幅防止溢出
            if (avg > Short.MAX_VALUE) avg = Short.MAX_VALUE;
            if (avg < Short.MIN_VALUE) avg = Short.MIN_VALUE;

            // 写入输出（小端）
            outputBytes[i * 2] = (byte) (avg & 0xFF);
            outputBytes[i * 2 + 1] = (byte) ((avg >> 8) & 0xFF);
        }

        return outputBytes;
    }
    
    /**
     * 向输入缓冲区写入数据
     *
     * @param data 输入数据
     */
    public void putData(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        
        // 创建ByteBuf并写入数据
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(data.length);
        try {
            byteBuf.writeBytes(data);
            inputRingBuffer.write(byteBuf);
        } finally {
            byteBuf.release();
        }
    }
    
    /**
     * 开始RTP发送
     */
    public void startRtpSender() {
        new Thread(() -> {
            boolean sendSilence = true;
            byte[] silenceData = new byte[160];
            Arrays.fill(silenceData, TTSConstant.TTS_SILENCE_BYTE);
            
            while (true) {
                try {
                    // 控制每次分包是160字节 * n
                    byte[] peek = outputRingBuffer.peek(EMConstant.VOIP_SAMPLES_PER_FRAME * 1000);
                    if (stop.get()) {
                        sender.close();
                        return;
                    }
                    
                    if (peek != null) sendSilence = false;
                    if (peek == null && sendSilence) {
                        sender.sendFrame(silenceData);
                        continue;
                    }
                    
                    int packageCount = peek.length / EMConstant.VOIP_SAMPLES_PER_FRAME;
                    int redundantData = peek.length % EMConstant.VOIP_SAMPLES_PER_FRAME;
                    // 解决Index -1 out of bounds for length 1问题
                    if (peek.length == 1 && peek[0] == TTSConstant.TTS_END_BYTE) {
                        putData(outputRingBuffer, TTSConstant.TTS_END_FLAG);
                        continue;
                    }
                    if (!(peek[peek.length - 2] == TTSConstant.TTS_END_BYTE) && 
                        !(peek[peek.length - 1] == TTSConstant.TTS_END_BYTE) && 
                        redundantData != 0) {
                        if (packageCount > 1) {
                            packageCount = packageCount - 1;
                        } else {
                            packageCount = 1;
                        }
                    } else if (packageCount == 0) {
                        packageCount = 1;
                    }
                    
                    byte[] payload = takeData(outputRingBuffer, EMConstant.VOIP_SAMPLES_PER_FRAME * packageCount);
                    log.trace("发送 {} 字节的RTP数据", payload.length);
                    sender.sendFrame(payload);
                    if (payload[payload.length - 2] == TTSConstant.TTS_END_BYTE && payload[payload.length - 1] == TTSConstant.TTS_END_BYTE) {
                        sendSilence = true;
                        SipUtils.executeTask(() -> callback.apply("completed"));
                        log.info("TTS播放完成，已触发回调");
                    }
                    if (payload[0] == TTSConstant.TTS_INTERRUPT_BYTE && payload[1] == TTSConstant.TTS_INTERRUPT_BYTE) {
                        sendSilence = true;
                        SipUtils.executeTask(() -> callback.apply("interrupt"));
                        log.info("语音流已经打断！！！");
                    }
                } catch (Exception e) {
                    log.error("RTP发送异常", e);
                }
            }
        }).start();
    }
    
    /**
     * 停止RTP发送
     */
    public void stopRtpSender() {
        stop.set(true);
        if (sender != null) {
            sender.close();
        }
        // 释放缓冲区资源
        releaseResources();
    }
    
    /**
     * 中断TTS播放
     */
    public void interrupt() {
        try {
            inputRingBuffer.clear();
            outputRingBuffer.clear();
            if (sender != null) {
                sender.interrupt();
            }
            putData(outputRingBuffer, TTSConstant.TTS_INTERRUPT_FLAG); // 结束标记
            log.info("已中断TTS播放");
        } catch (Exception e) {
            log.error("中断TTS播放时出现异常", e);
        }
    }
    
    /**
     * 辅助方法：从NettyCircularAudioBuffer读取数据
     */
    private byte[] takeData(NettyCircularAudioBuffer buffer, int maxLength) {
        if (buffer.getSize() == 0) {
            return null;
        }
        
        int actualLength = Math.min(maxLength, buffer.getSize());
        ByteBuf byteBuf = buffer.read(actualLength);
        
        if (byteBuf.readableBytes() == 0) {
            byteBuf.release();
            return null;
        }
        
        byte[] result = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(result);
        byteBuf.release();
        
        return result;
    }
    
    /**
     * 辅助方法：向NettyCircularAudioBuffer写入数据
     */
    private void putData(NettyCircularAudioBuffer buffer, byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(data.length);
        try {
            byteBuf.writeBytes(data);
            buffer.write(byteBuf);
        } finally {
            byteBuf.release();
        }
    }
    
    /**
     * 释放所有资源
     */
    public void releaseResources() {
        try {
            if (inputRingBuffer != null && !inputRingBuffer.isClosed()) {
                inputRingBuffer.release();
                log.info("已释放输入缓冲区资源");
            }
            if (outputRingBuffer != null && !outputRingBuffer.isClosed()) {
                outputRingBuffer.release();
                log.info("已释放输出缓冲区资源");
            }
        } catch (Exception e) {
            log.warn("释放缓冲区资源时出现异常", e);
        }
    }
    
    /**
     * 获取缓冲区状态信息
     */
    public String getBufferStatus() {
        return String.format("TTS缓冲区状态 - 输入：%s, 输出：%s", 
                inputRingBuffer.getStatusInfo(), 
                outputRingBuffer.getStatusInfo());
    }
    
    /**
     * 检查缓冲区健康状态
     */
    private void checkBufferHealth() {
        double inputUsage = inputRingBuffer.getUsageRatio();
        double outputUsage = outputRingBuffer.getUsageRatio();
        
        if (inputUsage > 0.9) {
            log.warn("输入缓冲区使用率过高：{:.1f}%，TTS数据积压！", inputUsage * 100);
        }
        if (outputUsage > 0.9) {
            log.warn("输出缓冲区使用率过高：{:.1f}%，RTP发送速度跟不上！", outputUsage * 100);
        }
        
        if (inputUsage > 0.8 || outputUsage > 0.8) {
            log.debug("缓冲区状态：{}", getBufferStatus());
        }
    }
} 