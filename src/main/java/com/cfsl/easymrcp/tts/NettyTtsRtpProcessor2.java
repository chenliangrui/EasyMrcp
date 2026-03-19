package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSender2;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简化版TTS音频处理器 - 直接PCM模式
 * 假设TTS直接输出8kHz 16bit PCM，FreeSWITCH协商使用L16编码
 * 省略inputRingBuffer、重采样、G.711编码等处理，直接写入outputRingBuffer发送
 */
@Slf4j
public class NettyTtsRtpProcessor2 {
    private NettyRtpSender2 sender;
    @Setter
    private TtsCallback callback;

    // 输出缓冲区配置
    private static final int TTS_OUTPUT_BUFFER_SECONDS = 30;
    private static final int SAMPLE_RATE = EMConstant.VOIP_SAMPLE_RATE;

    // 只保留outputRingBuffer，直接接收PCM数据
    private final NettyAudioRingBuffer outputRingBuffer;

    private final AtomicBoolean stop = new AtomicBoolean(false);

    /**
     * 需要跳过的末尾的字节数(PCM格式)
     * 例如使用：PCM 8kHz 16bit
     * 那么200ms = 8000 * 2 * 0.2 = 3200字节
     */
    @Setter
    private int skipBytesInTheEndPacket = 0;

    /**
     * 构造函数 - 简化版，直接使用L16 PCM模式
     *
     * @param remoteIp   远程IP地址
     * @param remotePort 远程端口
     * @param mediaType  sdp使用的编码，期望是96(L16)
     */
    public NettyTtsRtpProcessor2(String remoteIp, int remotePort, int mediaType) {
        // 初始化输出环形缓冲区
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
        this.outputRingBuffer = new NettyAudioRingBuffer(allocator, SAMPLE_RATE, TTS_OUTPUT_BUFFER_SECONDS, true);

        log.debug("TTS简化版处理器初始化完成，编码类型: {}", mediaType);

        try {
            // 创建RTP发送器
            this.sender = new NettyRtpSender2(remoteIp, remotePort);
            // 设置Payload Type
            this.sender.setPayloadType(mediaType);
        } catch (Exception e) {
            log.error("初始化NettyTtsRtpProcessor2失败", e);
        }
    }

    public void setRtpChannel(Channel channel) {
        sender.setRtpChannel(channel);
    }

    /**
     * 直接向输出缓冲区写入PCM数据（TTS引擎输出8kHz PCM）
     *
     * @param data      PCM数据
     * @param bytesRead 有效字节数
     */
    public void putData(byte[] data, int bytesRead) {
        if (data == null || data.length == 0 || bytesRead <= 0) {
            return;
        }

        // 调试打印音频数据信息
        log.debug("收到PCM数据，长度: {}字节，字节值分布: {}", bytesRead, printByteDistribution(data, bytesRead));

        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(bytesRead);
        byteBuf.writeBytes(data, 0, bytesRead);
        outputRingBuffer.write(byteBuf);
        byteBuf.release();
    }

    /**
     * 打印字节值分布统计，帮助调试音频数据是否有效
     */
    private String printByteDistribution(byte[] data, int length) {
        if (length <= 0) return "无数据";

        int min = 255, max = -256, zeroCount = 0;
        int sum = 0;

        for (int i = 0; i < length; i++) {
            int byteVal = data[i];
            if (byteVal < min) min = byteVal;
            if (byteVal > max) max = byteVal;
            if (byteVal == 0) zeroCount++;
            sum += byteVal;
        }

        double avg = sum / (double) length;
        return String.format("min=%d, max=%d, avg=%.2f, zeros=%d/%d",
                min, max, avg, zeroCount, length);
    }

    /**
     * 直接向输出缓冲区写入ByteBuf PCM数据
     *
     * @param data PCM数据ByteBuf
     */
    public void putData(ByteBuf data) {
        if (data == null || data.readableBytes() == 0) {
            return;
        }
        outputRingBuffer.write(data);
    }

    /**
     * 直接向输出缓冲区写入PCM数据（TTS引擎输出8kHz PCM）- 别名方法
     *
     * @param data      PCM数据
     * @param bytesRead 有效字节数
     */
    public void putPcmData(byte[] data, int bytesRead) {
        putData(data, bytesRead);
    }

    /**
     * 直接向输出缓冲区写入ByteBuf PCM数据 - 别名方法
     *
     * @param data PCM数据ByteBuf
     */
    public void putPcmData(ByteBuf data) {
        putData(data);
    }

    /**
     * 写入结束标记
     */
    public void writeEndFlag() {
        outputRingBuffer.write(TTSConstant.TTS_END_FLAG.retainedDuplicate());
    }

    /**
     * 开始RTP发送 - 复用原有逻辑
     */
    public void startRtpSender() {
        new Thread(() -> {
            boolean sendSilence = true;
            // 创建静音数据ByteBuf (8kHz 16bit 20ms = 320字节)
            ByteBuf silenceData = ByteBufAllocator.DEFAULT.buffer(EMConstant.VOIP_L16_BYTES_PER_FRAME);
            byte[] silenceBytes = new byte[EMConstant.VOIP_L16_BYTES_PER_FRAME];
            Arrays.fill(silenceBytes, TTSConstant.TTS_SILENCE_BYTE);
            silenceData.writeBytes(silenceBytes);

            while (true) {
                try {
                    // 控制每次分包是L16帧大小的倍数
                    ByteBuf peek = outputRingBuffer.peek(EMConstant.VOIP_L16_BYTES_PER_FRAME * 1000);
                    if (stop.get()) {
                        sender.close();
                        silenceData.release();
                        return;
                    }

                    if (peek != null && peek.readableBytes() > 0) {
                        sendSilence = false;

                        int packageCount = peek.readableBytes() / EMConstant.VOIP_L16_BYTES_PER_FRAME;
                        int redundantData = peek.readableBytes() % EMConstant.VOIP_L16_BYTES_PER_FRAME;

                        // 解决Index -1 out of bounds问题
                        if (peek.readableBytes() == 1 && peek.getByte(0) == TTSConstant.TTS_END_BYTE) {
                            peek.release();
                            outputRingBuffer.write(TTSConstant.TTS_END_FLAG.retainedDuplicate());
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

                        peek.release();

                        ByteBuf payload = takeDataAsByteBuf(outputRingBuffer, EMConstant.VOIP_L16_BYTES_PER_FRAME * packageCount);
                        if (payload != null && payload.readableBytes() > 0) {
                            // 检查结束/中断标记
                            boolean hasEndFlag = false;
                            boolean hasInterruptFlag = false;

                            if (payload.readableBytes() >= 2) {
                                byte endByte1 = payload.getByte(payload.readableBytes() - 2);
                                byte endByte2 = payload.getByte(payload.readableBytes() - 1);

                                if (endByte1 == TTSConstant.TTS_END_BYTE && endByte2 == TTSConstant.TTS_END_BYTE) {
                                    hasEndFlag = true;
                                    payload.writerIndex(payload.writerIndex() - 2);
                                } else if (endByte1 == TTSConstant.TTS_INTERRUPT_BYTE && endByte2 == TTSConstant.TTS_INTERRUPT_BYTE) {
                                    hasInterruptFlag = true;
                                    payload.writerIndex(payload.writerIndex() - 2);
                                }
                            }

                            // 直接发送PCM数据（L16编码，不需要G.711转换）
                            if (payload.readableBytes() > 0) {
                                sender.sendFrame(payload);
                            }

                            // 处理结束标记
                            if (hasEndFlag) {
                                sendSilence = true;
                                SipUtils.executeTask(() -> callback.apply("completed"));
                                log.info("tts播放完成");
                            }

                            // 处理中断标记
                            if (hasInterruptFlag) {
                                sendSilence = true;
                                SipUtils.executeTask(() -> callback.apply("interrupt"));
                                log.info("tts语音流已经被打断");
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
        releaseResources();
    }

    /**
     * 中断TTS播放
     */
    public void interrupt() {
        try {
            outputRingBuffer.clear();
            if (sender != null) {
                sender.interrupt();
            }
            outputRingBuffer.write(TTSConstant.TTS_INTERRUPT_FLAG.retainedDuplicate());
            log.debug("已中断TTS播放");
        } catch (Exception e) {
            log.error("中断TTS播放时出现异常", e);
        }
    }

    /**
     * 将ByteBuf内容转换为十六进制字符串（前5000个字节）
     */
    private String byteBufToHex(ByteBuf buf) {
        int len = Math.min(buf.readableBytes(), 5000);
        byte[] bytes = new byte[len];
        buf.getBytes(buf.readerIndex(), bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        if (buf.readableBytes() > 5000) {
            sb.append("...");
        }
        return sb.toString();
    }

    /**
     * 从缓冲区读取数据
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
     * 释放所有资源
     */
    public void releaseResources() {
        try {
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
        return String.format("TTS简化版缓冲区状态 - 输出：%s",
                outputRingBuffer.getStatusInfo());
    }
}
