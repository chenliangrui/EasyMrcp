package com.cfsl.easymrcp.asr;

import com.cfsl.easymrcp.domain.AsrConfig;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.mrcp.MrcpTimeoutManager;
import com.cfsl.easymrcp.rtp.NettyAsrRtpProcessor;
import com.cfsl.easymrcp.rtp.MrcpConnection;
import com.cfsl.easymrcp.rtp.RtpManager;
import com.cfsl.easymrcp.vad.VadHandle;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 管理一通电话中的asr相关的操作
 * 主要侧重于与不同厂家asr客户端的集成，处理音频流、控制asr客户端生命周期等操作
 */
@Data
@Slf4j
public abstract class AsrHandler implements MrcpConnection {
    @Setter
    @Getter
    private String channelId;
    @Setter
    private AsrCallback callback;
    protected String identifyPatterns;
    protected String reSample;
    @Setter
    @Getter
    private String callId;
    @Setter
    private MrcpTimeoutManager timeoutManager;
    @Setter
    private Boolean automaticInterruption;
    
    // 保存Speech-Complete-Timeout参数值
    private Long speechCompleteTimeout;

    // RTP相关
    private NettyAsrRtpProcessor nettyAsrRtpProcessor;
    private RtpManager rtpManager;

    protected Boolean stop = false;
    protected CountDownLatch countDownLatch = new CountDownLatch(1);
    private VadHandle vadHandle;

    public void setConfig(AsrConfig asrConfig) {
        if (asrConfig.getIdentifyPatterns() != null && !asrConfig.getIdentifyPatterns().isEmpty()) {
            this.identifyPatterns = asrConfig.getIdentifyPatterns();
        }
        if (asrConfig.getReSample() != null && !asrConfig.getReSample().isEmpty()) {
            this.reSample = asrConfig.getReSample();
        }
    }

    @Override
    public void create(String remoteIp, int remotePort) {
        nettyAsrRtpProcessor = new NettyAsrRtpProcessor();
        nettyAsrRtpProcessor.setReceive(this::receive);
        nettyAsrRtpProcessor.setReCreate(this::reCreate);
        nettyAsrRtpProcessor.setSendEof(this::sendEof);
        create();
        try {
            boolean await = countDownLatch.await(5000, TimeUnit.MILLISECONDS);
            if (!await) {
                log.warn("Did you forget to manually unblock after successfully connecting to ASR???");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 设置Speech-Complete-Timeout参数值
     * @param timeout Speech-Complete-Timeout参数值（毫秒）
     */
    public void setSpeechCompleteTimeout(Long timeout) {
        this.speechCompleteTimeout = timeout;
        // 如果VAD已经初始化，则更新其Speech-Complete-Timeout参数
        if (vadHandle != null) {
            vadHandle.setSpeechCompleteTimeout(timeout);
        }
    }

    /**
     * 启动ASR RTP接收
     */
    public void receive() {
        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
            // 使用Speech-Complete-Timeout参数初始化VAD
            vadHandle = speechCompleteTimeout != null ? new VadHandle(speechCompleteTimeout) : new VadHandle();
        }
        nettyAsrRtpProcessor.setVadHandle(vadHandle);
        nettyAsrRtpProcessor.setIdentifyPatterns(identifyPatterns);
        nettyAsrRtpProcessor.setReSample(reSample);
    }

    @Override
    public void close() {
        stop = true;
        // 关闭ASR客户端
        asrClose();
        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns) && vadHandle != null) {
            vadHandle.release();
        }
        // 取消所有超时定时器
        cancelTimeouts();
        timeoutManager = null;
    }
    
    /**
     * 取消所有超时定时器
     */
    public void cancelTimeouts() {
        if (timeoutManager != null) {
            timeoutManager.cancelAllTimers();
        }
    }
    
    /**
     * 响应START_INPUT_TIMERS请求，启动超时计时器
     */
    public void startInputTimers() {
        if (timeoutManager != null) {
            timeoutManager.startInputTimers();
        }
    }

    public abstract void create();

    /**
     * 处理PCM音频数据
     * 
     * @param pcmData PCM音频数据
     */
    public abstract void receive(byte[] pcmData);

    /**
     * 一句话语音识别情况下通知该asr客户端已经完成一句话识别的音频输入，实时语音识别可不做处理
     * 注意该asr的连接应该在asr异步返回结果后手动关闭该asr连接，系统没有对此进行封装。
     * 在该接口实现中应当完成以下操作：
     * 1. 发送该asr客户端的eof消息（一句话语音识别应该都有eof的设计）
     */
    public abstract void sendEof();

    /**
     * 实时语音识别情况下关闭与ipPBX的连接，一句话语音识别可不做处理
     * 当通话完全结束时，会由sip bye触发调用此方法。因为一个实时语音识别覆盖了整个通话过程，
     * 所以一次通话结束，就意味着整个实时语音识别结束。
     * 在该接口实现中应当完成以下操作：
     * 1. 发送该asr客户端的eof消息（如果某个asr有eof的设计）
     * 2. 关闭该asr客户端的连接
     */
    public abstract void asrClose();

    private void reCreate() {
        countDownLatch = new CountDownLatch(1);
        this.create();
        try {
            boolean await = countDownLatch.await(5000, TimeUnit.MILLISECONDS);
            if (!await) {
                log.warn("Did you forget to manually unblock after successfully connecting to ASR???");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
