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
    private String reSample;
    @Setter
    private VadHandle vadHandle;
    @Setter
    private String identifyPatterns; // 添加识别模式

    @Setter
    Consumer<byte[]> receive;
    @Setter
    Callback reCreate;
    @Setter
    Callback sendEof;

    // 状态标志
    private final AtomicBoolean stop = new AtomicBoolean(false);

    /**
     * 处理接收到的RTP数据，返回解码后的PCM数据
     *
     * @param data RTP数据
     * @return 解码后的PCM数据
     */
    private byte[] processRtpData(byte[] data) {
        try {
            // 解析RTP头部
            RtpPacket parsedPacket = RtpPacket.parseRtpHeader(data, data.length);
            // 提取G.711a负载
            byte[] g711Data = parsedPacket.getPayload();
            // G.711a解码为PCM
            byte[] pcmData = G711AUtil.decode(g711Data);

            // 处理重采样
            if (reSample != null && reSample.equals("upsample8kTo16k")) {
                return ReSample.resampleFrame(pcmData);
            } else {
                return pcmData;
            }
        } catch (Exception e) {
            log.error("处理RTP数据包异常", e);
            return null;
        }
    }

    /**
     * PCM聚合Handler，聚合到2048字节后下发
     */
    private class PcmAggregatorHandler extends ChannelInboundHandlerAdapter {
        private ByteBuf pcmBuffer = Unpooled.buffer();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                pcmBuffer.writeBytes((ByteBuf) msg);
                ((ByteBuf) msg).release();
                while (pcmBuffer.readableBytes() >= 2048) {
                    ByteBuf chunk = pcmBuffer.readRetainedSlice(2048);
                    ctx.fireChannelRead(chunk);
                }
            } else {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            pcmBuffer.release();
            super.handlerRemoved(ctx);
        }
    }

    /**
     * 业务处理Handler，处理2048字节PCM块，包含VAD和回调逻辑
     */
    private class AsrBusinessHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] take = new byte[msg.readableBytes()];
            msg.readBytes(take);
            try {
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
            } catch (Exception e) {
                log.error("处理PCM数据异常", e);
            }
        }
    }

    @Override
    protected void initChannel(DatagramChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("rtpHandler", new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                ByteBuf content = msg.content();
                int length = content.readableBytes();
                byte[] data = new byte[length];
                content.getBytes(content.readerIndex(), data);
                // 处理接收到的RTP数据，解码为PCM
                byte[] pcm = processRtpData(data);
                if (pcm != null && pcm.length > 0) {
                    ctx.fireChannelRead(Unpooled.wrappedBuffer(pcm));
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                log.error("RTP通道异常", cause);
            }
        });
        pipeline.addLast("pcmAggregator", new PcmAggregatorHandler());
        pipeline.addLast("asrBusinessHandler", new AsrBusinessHandler());
    }
} 