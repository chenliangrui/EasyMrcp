package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.G711AUtil;
import com.cfsl.easymrcp.rtp.NettyRtpSender;
import com.cfsl.easymrcp.rtp.RtpManager;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTS音频处理器
 * 负责处理和发送TTS合成的音频数据
 */
@Slf4j
public class NettyTtsRtpProcessor {
    private NettyRtpSender sender;
    @Setter
    private TtsCallback callback;
    private String reSample;
    
    // 线程间缓冲队列
    private final RingBuffer inputRingBuffer = new RingBuffer(1000000);
    private final RingBuffer outputQueue = new RingBuffer(1000000);
    
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
                    while (inputRingBuffer.getAvailable() == 0 || inputRingBuffer.getAvailable() < receiveTakeBytes) {
                        // 判断是否含有结束标志，有的话取出最后的音频数据
                        if (inputRingBuffer.getAvailable() > 0) {
                            byte[] peek = inputRingBuffer.peek(receiveTakeBytes);
                            if (peek[peek.length - 2] == TTSConstant.TTS_END_BYTE && peek[peek.length - 1] == TTSConstant.TTS_END_BYTE) {
                                break;
                            }
                        }
                        Thread.sleep(200); // 非阻塞等待
                        if (stop.get()) {
                            log.info("音频处理线程已停止");
                            return;
                        }
                    }
                    byte[] pcmData = inputRingBuffer.take(receiveTakeBytes);
                    byte[] processedData;
                    if (reSample != null && reSample.equals("downsample24kTo8k")) {
                        processedData = downsample24kTo8k(pcmData);
                    } else {
                        processedData = pcmData;
                    }
                    byte[] g711Data = G711AUtil.encode(processedData);
                    outputQueue.put(g711Data);
                    if (pcmData[pcmData.length - 2] == TTSConstant.TTS_END_BYTE && pcmData[pcmData.length - 1] == TTSConstant.TTS_END_BYTE) {
                        // 结束标记
                        outputQueue.put(TTSConstant.TTS_END_FLAG);
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
        inputRingBuffer.put(data);
    }
    
    /**
     * 开始RTP发送
     */
    public void startRtpSender() {
        new Thread(() -> {
            boolean sendSilence = true;
            byte[] silenceData = new byte[160];
            Arrays.fill(silenceData, (byte) 0xd5);
            
            while (true) {
                try {
                    // 控制每次分包是160字节 * n
                    byte[] peek = outputQueue.peek(EMConstant.VOIP_SAMPLES_PER_FRAME * 1000);
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
                    
                    byte[] payload = outputQueue.take(EMConstant.VOIP_SAMPLES_PER_FRAME * packageCount);
                    log.trace("发送 {} 字节的RTP数据", payload.length);
                    sender.sendFrame(payload);
                    if (payload[payload.length - 2] == TTSConstant.TTS_END_BYTE && payload[payload.length - 1] == TTSConstant.TTS_END_BYTE) {
                        sendSilence = true;
                        callback.apply("completed");
                        log.info("TTS播放完成，已触发回调");
                    }
                    if (payload[0] == TTSConstant.TTS_INTERRUPT_BYTE && payload[1] == TTSConstant.TTS_INTERRUPT_BYTE) {
                        sendSilence = true;
                        log.info("语音流已经打断！！！");
                        callback.apply("interrupt");
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
    }
    
    /**
     * 中断TTS播放
     */
    public void interrupt() {
        inputRingBuffer.clear();
        outputQueue.clear();
        if (sender != null) {
            sender.interrupt();
        }
        outputQueue.put(TTSConstant.TTS_INTERRUPT_FLAG); // 结束标记
        log.info("已中断TTS播放");
    }
} 