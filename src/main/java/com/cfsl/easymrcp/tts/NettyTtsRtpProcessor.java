package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.G711AUtil;
import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
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
    @Setter
    private String reSample;

    // 缓冲区配置：支持大容量TTS音频数据
    private static final int TTS_INPUT_BUFFER_SECONDS = 30;  // 输入缓冲区
    private static final int TTS_OUTPUT_BUFFER_SECONDS = 30; // 输出缓冲区
    private static final int SAMPLE_RATE = EMConstant.VOIP_SAMPLE_RATE; // 8kHz

    // 线程间缓冲队列 - 使用NettyCircularAudioBuffer替代RingBuffer
    private final NettyAudioRingBuffer inputRingBuffer;
    private final NettyAudioRingBuffer outputRingBuffer;

    private final AtomicBoolean stop = new AtomicBoolean(false);

    // 为解决24khz采样率重采样到8kHz采样率时数据长度不为6的倍数时导致的偶发的噪声问题，统一读取6 * n字节
    private int receiveTakeBytes = 6 * 500;

    /**
     * 构造函数 - 使用RtpManager
     *
     * @param remoteIp   远程IP地址
     * @param remotePort 远程端口
     */
    public NettyTtsRtpProcessor(String remoteIp, int remotePort) {
        // 初始化高性能环形缓冲区，TTS模式支持自动扩容
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
        this.inputRingBuffer = new NettyAudioRingBuffer(allocator, SAMPLE_RATE, TTS_INPUT_BUFFER_SECONDS, true);  // TTS模式
        this.outputRingBuffer = new NettyAudioRingBuffer(allocator, SAMPLE_RATE, TTS_OUTPUT_BUFFER_SECONDS, true); // TTS模式

        log.debug("TTS缓冲区初始化完成");

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
                            ByteBuf peek = inputRingBuffer.peek(receiveTakeBytes);
                            if (peek != null && peek.readableBytes() >= 2 &&
                                    peek.getByte(peek.readableBytes() - 2) == TTSConstant.TTS_END_BYTE &&
                                    peek.getByte(peek.readableBytes() - 1) == TTSConstant.TTS_END_BYTE) {
                                peek.release();
                                break;
                            }
                            if (peek != null) {
                                peek.release();
                            }
                        }
                        Thread.sleep(200);
                        if (stop.get()) {
                            log.debug("音频处理线程已停止");
                            return;
                        }
                    }

                    // 从输入缓冲区读取数据
                    ByteBuf pcmData = takeDataAsByteBuf(inputRingBuffer, receiveTakeBytes);
                    ByteBuf duplicate = pcmData.duplicate();
                    if (pcmData != null && pcmData.readableBytes() > 0) {
                        ByteBuf processedData;
                        if (reSample != null && reSample.equals("downsample24kTo8k")) {
                            processedData = downsample24kTo8k(pcmData);
                        } else {
                            processedData = pcmData;
                        }
                        ByteBuf g711Data = G711AUtil.encode(processedData);
                        putData(outputRingBuffer, g711Data);
                        g711Data.release();

                        // 检查结束标记
                        if (duplicate.readableBytes() >= 2 &&
                                duplicate.getByte(duplicate.readableBytes() - 2) == TTSConstant.TTS_END_BYTE &&
                                duplicate.getByte(duplicate.readableBytes() - 1) == TTSConstant.TTS_END_BYTE) {
                            // 结束标记
                            putData(outputRingBuffer, TTSConstant.TTS_END_FLAG.retainedDuplicate());
                        }

                        // 释放资源
                        if (processedData != pcmData) {
                            processedData.release();
                        }
                        pcmData.release();
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
     * 将PCM字节流从24kHz降采样到8kHz (ByteBuf版本，避免内存拷贝)
     */
    public static ByteBuf downsample24kTo8k(ByteBuf input) {
        if (input == null || input.readableBytes() == 0) {
            return ByteBufAllocator.DEFAULT.buffer(0);
        }

        int sampleSize = 2; // 每个采样点16-bit，即2字节
        int ratio = 3;      // 24kHz -> 8kHz，每3个采样点降为1个
        int totalSamples = input.readableBytes() / sampleSize;
        int newSamples = totalSamples / ratio;

        ByteBuf output = ByteBufAllocator.DEFAULT.buffer(newSamples * sampleSize);

        for (int i = 0; i < newSamples; i++) {
            int idx1 = i * ratio * sampleSize;
            int idx2 = idx1 + sampleSize;
            int idx3 = idx2 + sampleSize;

            // 防止越界（尾部可能不足3个采样点）
            if (idx3 + 1 >= input.readableBytes()) {
                break;
            }

            // 读取3个采样值（每个采样是2字节，小端）
            int s1 = (input.getByte(idx1 + 1) << 8) | (input.getByte(idx1) & 0xFF);
            int s2 = (input.getByte(idx2 + 1) << 8) | (input.getByte(idx2) & 0xFF);
            int s3 = (input.getByte(idx3 + 1) << 8) | (input.getByte(idx3) & 0xFF);

            int avg = (s1 + s2 + s3) / 3;

            // 限幅防止溢出
            if (avg > Short.MAX_VALUE) avg = Short.MAX_VALUE;
            if (avg < Short.MIN_VALUE) avg = Short.MIN_VALUE;

            // 写入输出（小端）
            output.writeByte((byte) (avg & 0xFF));
            output.writeByte((byte) ((avg >> 8) & 0xFF));
        }

        return output;
    }

    /**
     * 向输入缓冲区写入数据
     *
     * @param data      输入数据
     * @param bytesRead
     */
    public void putData(byte[] data, int bytesRead) {
        if (data == null || data.length == 0) {
            return;
        }

        // 直接创建ByteBuf并写入数据，避免额外的内存拷贝
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(bytesRead);
        byteBuf.writeBytes(data, 0 , bytesRead);
        inputRingBuffer.write(byteBuf);
        byteBuf.release();
    }

    /**
     * 向输入缓冲区写入ByteBuf数据
     *
     * @param data 输入ByteBuf数据
     */
    public void putData(ByteBuf data) {
        if (data == null || data.readableBytes() == 0) {
            return;
        }

        // 直接写入ByteBuf，零拷贝
        inputRingBuffer.write(data);
    }

    /**
     * 开始RTP发送
     */
    public void startRtpSender() {
        new Thread(() -> {
            boolean sendSilence = true;
            // 创建静音数据ByteBuf，避免使用array()方法
            ByteBuf silenceData = ByteBufAllocator.DEFAULT.buffer(160);
            byte[] silenceBytes = new byte[160];
            Arrays.fill(silenceBytes, TTSConstant.TTS_SILENCE_BYTE);
            silenceData.writeBytes(silenceBytes);

            while (true) {
                try {
                    // 控制每次分包是160字节 * n
                    ByteBuf peek = outputRingBuffer.peek(EMConstant.VOIP_SAMPLES_PER_FRAME * 1000);
                    if (stop.get()) {
                        sender.close();
                        silenceData.release(); // 释放silenceData
                        return;
                    }

                    if (peek != null && peek.readableBytes() > 0) {
                        sendSilence = false;

                        int packageCount = peek.readableBytes() / EMConstant.VOIP_SAMPLES_PER_FRAME;
                        int redundantData = peek.readableBytes() % EMConstant.VOIP_SAMPLES_PER_FRAME;

                        // 解决Index -1 out of bounds for length 1问题
                        if (peek.readableBytes() == 1 && peek.getByte(0) == TTSConstant.TTS_END_BYTE) {
                            peek.release();
                            // 直接使用ByteBuf常量
                            putData(outputRingBuffer, TTSConstant.TTS_END_FLAG.retainedDuplicate());
                            continue;
                        }

                        if (!(peek.getByte(peek.readableBytes() - 2) == TTSConstant.TTS_END_BYTE) &&
                                !(peek.getByte(peek.readableBytes() - 1) == TTSConstant.TTS_END_BYTE) &&
                                redundantData != 0) {
                            if (packageCount > 1) {
                                packageCount = packageCount - 1;
                            } else {
                                packageCount = 1;
                            }
                        } else if (packageCount == 0) {
                            packageCount = 1;
                        }

                        peek.release(); // 释放peek的ByteBuf

                        // 使用ByteBuf版本避免内存拷贝
                        ByteBuf payload = takeDataAsByteBuf(outputRingBuffer, EMConstant.VOIP_SAMPLES_PER_FRAME * packageCount);
                        if (payload != null && payload.readableBytes() > 0) {
                            log.trace("发送 {} 字节的RTP数据", payload.readableBytes());
                            sender.sendFrame(payload);

                            // 检查结束标记 - 直接使用ByteBuf的getByte方法
                            if (payload.readableBytes() >= 2) {
                                byte endByte1 = payload.getByte(payload.readableBytes() - 2);
                                byte endByte2 = payload.getByte(payload.readableBytes() - 1);

                                if (endByte1 == TTSConstant.TTS_END_BYTE && endByte2 == TTSConstant.TTS_END_BYTE) {
                                    sendSilence = true;
                                    SipUtils.executeTask(() -> callback.apply("completed"));
                                    log.info("tts播放完成");
                                }
                                if (endByte1 == TTSConstant.TTS_INTERRUPT_BYTE && endByte2 == TTSConstant.TTS_INTERRUPT_BYTE) {
                                    sendSilence = true;
                                    SipUtils.executeTask(() -> callback.apply("interrupt"));
                                    log.info("tts语音流已经被打断");
                                }
                            }
                            payload.release();
                        }
                    } else if (sendSilence) {
                        sender.sendFrame(silenceData);
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
            // 直接使用ByteBuf常量
            putData(outputRingBuffer, TTSConstant.TTS_INTERRUPT_FLAG.retainedDuplicate());
            log.debug("已中断TTS播放");
        } catch (Exception e) {
            log.error("中断TTS播放时出现异常", e);
        }
    }

    /**
     * 辅助方法：从NettyCircularAudioBuffer读取数据，返回ByteBuf
     */
    private ByteBuf takeDataAsByteBuf(NettyAudioRingBuffer buffer, int maxLength) {
        if (buffer.getSize() == 0) {
            return null;
        }

        int actualLength = Math.min(maxLength, buffer.getSize());
        ByteBuf byteBuf = buffer.read(actualLength);

        if (byteBuf.readableBytes() == 0) {
            byteBuf.release();
            return null;
        }

        return byteBuf;
    }

    /**
     * 辅助方法：向NettyCircularAudioBuffer写入ByteBuf数据
     */
    private void putData(NettyAudioRingBuffer buffer, ByteBuf data) {
        if (data == null || data.readableBytes() == 0) {
            return;
        }

        // 直接写入ByteBuf，零拷贝
        buffer.write(data);
    }

    /**
     * 释放所有资源
     */
    public void releaseResources() {
        try {
            if (inputRingBuffer != null && !inputRingBuffer.isClosed()) {
                inputRingBuffer.release();
                log.debug("已释放输入缓冲区资源");
            }
            if (outputRingBuffer != null && !outputRingBuffer.isClosed()) {
                outputRingBuffer.release();
                log.debug("已释放输出缓冲区资源");
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