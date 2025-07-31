package com.cfsl.easymrcp.rtp;

import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.mrcp.Callback;
import com.cfsl.easymrcp.utils.ReSample;
import com.cfsl.easymrcp.utils.SipUtils;
import com.cfsl.easymrcp.vad.VadHandle;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * ASR RTP音频处理器
 * 负责接收和处理ASR的RTP音频数据，支持VAD检测和音频缓存
 */
@Slf4j
public class NettyAsrRtpProcessor extends ChannelInitializer<DatagramChannel> {
    @Setter
    private String reSample;
    @Setter
    private VadHandle vadHandle;
    @Setter
    private String identifyPatterns;

    @Setter
    Consumer<byte[]> receive;
    @Setter
    Callback reCreate;
    @Setter
    Callback sendEof;

    // 音频缓冲区，在构造时创建
    private NettyCircularAudioBuffer audioBuffer;
    private final int SEND_CHUNK_SIZE = 2048;
    private volatile boolean isReconnecting = false;
    // 防止出现vad结束时asr还没连接成功而导致无法发送eof问题
    private volatile boolean connectedAndSendRemain = false;

    /**
     * 初始化音频缓冲区
     */
    public void initializeBuffer(ByteBufAllocator allocator) {
        if (audioBuffer == null) {
            int sampleRate = (reSample != null && reSample.equals("upsample8kTo16k")) ? 16000 : 8000;
            // 固定3秒缓冲容量
            audioBuffer = new NettyCircularAudioBuffer(allocator, sampleRate);
            log.info("初始化音频缓冲区，采样率: {}Hz, 固定缓冲: 3秒", sampleRate);
        }
    }

    /**
     * 处理接收到的RTP数据，返回解码后的PCM数据
     */
    private ByteBuf processRtpData(ByteBuf rtpData, ByteBufAllocator allocator) {
        try {
            byte[] rtpBytes = new byte[rtpData.readableBytes()];
            rtpData.getBytes(rtpData.readerIndex(), rtpBytes);

            RtpPacket parsedPacket = RtpPacket.parseRtpHeader(rtpBytes, rtpBytes.length);
            byte[] g711Data = parsedPacket.getPayload();
            byte[] pcmData = G711AUtil.decode(g711Data);

            if (reSample != null && reSample.equals("upsample8kTo16k")) {
                pcmData = ReSample.resampleFrame(pcmData);
            }

            return allocator.buffer(pcmData.length).writeBytes(pcmData);
        } catch (Exception e) {
            log.error("处理RTP数据包异常", e);
            return null;
        }
    }

        /**
     * 业务处理Handler，直接处理PCM数据
     */
    private class AsrBusinessHandler extends SimpleChannelInboundHandler<ByteBuf> {
        // VAD检测需要的聚合缓冲区（2048字节帧）
        private ByteBuf vadBuffer;
        private final int VAD_FRAME_SIZE = 2048;
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            try {
                // 确保缓冲区已初始化
                if (audioBuffer == null) {
                    initializeBuffer(ctx.alloc());
                }
                
                // 初始化VAD缓冲区
                if (vadBuffer == null) {
                    vadBuffer = ctx.alloc().buffer(VAD_FRAME_SIZE * 2);
                }

                if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
                    handleVadMode(ctx, msg);
                } else {
                    // 非VAD模式，直接发送
                    sendAudioData(msg);
                }
            } catch (Exception e) {
                log.error("处理PCM数据异常", e);
            }
        }

                /**
         * 处理VAD模式，使用2048字节帧进行VAD检测
         * 依靠PCM接收节奏驱动VAD检测，每次只处理一个帧
         */
        private void handleVadMode(ChannelHandlerContext ctx, ByteBuf msg) {
            // 所有音频数据都先写入主缓冲区
            audioBuffer.write(msg);
            
            // 将数据添加到VAD缓冲区进行聚合
            vadBuffer.writeBytes(msg);
            
            // 只处理一个VAD帧，保持与PCM接收的自然节奏
            if (vadBuffer.readableBytes() >= VAD_FRAME_SIZE) {
                // 读取一个2048字节的音频帧
                byte[] vadFrame = new byte[VAD_FRAME_SIZE];
                vadBuffer.readBytes(vadFrame);
                
                // 进行VAD检测
                boolean isSpeakingBefore = vadHandle.getIsSpeaking();
                vadHandle.receivePcm(vadFrame);
                boolean isSpeakingNow = vadHandle.getIsSpeaking();
                
                // 处理VAD状态变化
                if (isSpeakingNow) {
                    if (!isSpeakingBefore && !isReconnecting) {
                        // 语音开始，移动读指针到500ms前，然后异步连接ASR
                        log.info("VAD检测到语音开始，移动读指针到500ms前");
                        audioBuffer.moveReadPointerBack(500);
                        asyncReconnectAsr();
                    }
                    
                    // 语音期间，如果不在连接中则发送数据
                    if (!isReconnecting) {
                        sendBufferedAudio();
                    }
                } else if (isSpeakingBefore) {
                    log.info("VAD检测到语音结束");
                    if (isReconnecting) {
                        connectedAndSendRemain = true;
                    } else {
                        // 语音结束，发送所有剩余数据
                        sendRemainingAudio();
                        sendEof.execute();
                    }
                }
            }
        }

        /**
         * 异步连接ASR
         */
        private void asyncReconnectAsr() {
            isReconnecting = true;
            SipUtils.executeTask(() -> {
                try {
                    log.info("开始ASR连接...");
                    reCreate.execute();
                    log.info("ASR连接成功，准备发送缓存音频");

                    // 连接成功，标记为非连接状态，后续音频处理会自动发送缓存数据
                    isReconnecting = false;
                    if (connectedAndSendRemain) {
                        sendRemainingAudio();
                        sendEof.execute();
                        connectedAndSendRemain = false;
                    }
                } catch (Exception e) {
                    log.error("ASR连接失败", e);
                    audioBuffer.clear();
                    isReconnecting = false;
                }
            });
        }

        /**
         * 发送缓冲区中的音频数据（每次只发送一个块，保持时间间隔）
         */
        private void sendBufferedAudio() {
            // 只发送一个块，通过接收PCM的节奏来驱动发送时序
            if (audioBuffer.getSize() >= SEND_CHUNK_SIZE) {
                ByteBuf chunk = audioBuffer.read(SEND_CHUNK_SIZE);
                if (chunk.readableBytes() > 0) {
                    sendAudioData(chunk);
                }
                chunk.release();
            }
        }

        /**
         * 发送所有剩余的音频数据（语音结束时调用）
         */
        private void sendRemainingAudio() {
            // 语音结束时，发送所有剩余数据
            ByteBuf remaining = audioBuffer.readAll();
            if (remaining.readableBytes() > 0) {
                log.info("发送剩余音频数据: {}字节", remaining.readableBytes());

                // 按块发送剩余数据
                int offset = 0;
                int totalSize = remaining.readableBytes();
                while (offset < totalSize) {
                    int chunkSize = Math.min(SEND_CHUNK_SIZE, totalSize - offset);
                    ByteBuf chunk = remaining.retainedSlice(offset, chunkSize);
                    sendAudioData(chunk);
                    chunk.release();
                    offset += chunkSize;
                }
            }
            remaining.release();
            audioBuffer.clear();
        }


        /**
         * 发送音频数据
         */
        private void sendAudioData(ByteBuf audioBuf) {
            byte[] audioBytes = new byte[audioBuf.readableBytes()];
            audioBuf.getBytes(audioBuf.readerIndex(), audioBytes);
            receive.accept(audioBytes);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (audioBuffer != null) {
                log.info("释放音频缓冲区: {}", audioBuffer.getStatusInfo());
                audioBuffer.release();
            }
            if (vadBuffer != null) {
                vadBuffer.release();
                log.debug("释放VAD缓冲区");
            }
            super.handlerRemoved(ctx);
        }
    }

    @Override
    protected void initChannel(DatagramChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // RTP处理器
        pipeline.addLast("rtpHandler", new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                ByteBuf content = msg.content();
                ByteBuf pcm = processRtpData(content, ctx.alloc());
                if (pcm != null && pcm.readableBytes() > 0) {
                    ctx.fireChannelRead(pcm);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                log.error("RTP通道异常", cause);
            }
        });

        // 直接连接业务处理器，移除PcmAggregatorHandler
        pipeline.addLast("asrBusinessHandler", new AsrBusinessHandler());
    }
} 