package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.mrcp.Callback;
import com.cfsl.easymrcp.tts.RingBuffer;
import com.cfsl.easymrcp.utils.ReSample;
import com.cfsl.easymrcp.vad.VadHandle;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.Null;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * ASR RTP音频处理器
 * 负责接收和处理ASR的RTP音频数据
 */
@Slf4j
public class NettyAsrRtpProcessor extends ChannelInitializer<DatagramChannel> {
    @Setter
    private Channel channel;
    @Getter
    private final RingBuffer inputRingBuffer = new RingBuffer(1000000);
    private String reSample;
    @Setter
    private VadHandle vadHandle;
    private String identifyPatterns; // 添加识别模式

    @Setter
    Consumer<byte[]> receive;
    @Setter
    Callback reCreate;
    @Setter
    Callback sendEof;

    // 状态标志
    private final AtomicBoolean bound = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean stop = new AtomicBoolean(false);

    /**
     * 处理接收到的RTP数据
     *
     * @param data RTP数据
     */
    private void processRtpData(byte[] data) {
        try {
            // 解析RTP头部
            RtpPacket parsedPacket = RtpPacket.parseRtpHeader(data, data.length);
            // 提取G.711a负载
            byte[] g711Data = parsedPacket.getPayload();
            // G.711a解码为PCM
            byte[] pcmData = G711AUtil.decode(g711Data);

            // 处理重采样
            if (reSample != null && reSample.equals("upsample8kTo16k")) {
                byte[] bytes = ReSample.resampleFrame(pcmData);
                inputRingBuffer.put(bytes);
            } else {
                inputRingBuffer.put(pcmData);
            }
        } catch (Exception e) {
            log.error("处理RTP数据包异常", e);
        }
    }

    /**
     * 启动音频处理线程
     */
    public void startAudioProcessing() {
        CompletableFuture.runAsync(() -> {
            while (!stop.get()) {
                try {
                    if (inputRingBuffer.getAvailable() >= 2048) {
                        byte[] take = inputRingBuffer.take(2048);
                        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
                            boolean isSpeakingBefore = vadHandle.getIsSpeaking();
                            vadHandle.receivePcm(take);
                            boolean isSpeakingNow = vadHandle.getIsSpeaking();
                            if (isSpeakingNow) {
                                if (!isSpeakingBefore) {
                                    log.info("VAD检测到语音开始");
                                    reCreate.execute();
                                }
                                receive.accept(take);
                            } else if (isSpeakingBefore) {
                                log.info("VAD检测到语音结束");
                                sendEof.execute();
                            }
                        } else {
                            receive.accept(take);
                        }
                    } else {
                        // 避免CPU占用过高
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (Exception e) {
                    log.error("处理PCM数据异常", e);
                }
            }
        });
    }
    /**
     * 关闭处理器
     */
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }

        stop.set(true);

        if (channel != null) {
            channel.close();
            channel = null;
            bound.set(false);
            log.info("ASR RTP通道已关闭");
        }

        // 释放VAD资源
        if (vadHandle != null) {
            vadHandle.release();
            vadHandle = null;
        }
    }

    @Override
    protected void initChannel(DatagramChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("rtpHandler", new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                ByteBuf content = msg.content();
                int length = content.readableBytes();
                byte[] data = new byte[length];
                content.getBytes(content.readerIndex(), data);
                // 处理接收到的RTP数据
                processRtpData(data);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                log.error("RTP通道异常", cause);
            }
        });
    }
} 