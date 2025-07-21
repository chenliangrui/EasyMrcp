package com.cfsl.easymrcp.asr;

import com.cfsl.easymrcp.domain.AsrConfig;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.mrcp.MrcpTimeoutManager;
import com.cfsl.easymrcp.rtp.G711AUtil;
import com.cfsl.easymrcp.rtp.RtpConnection;
import com.cfsl.easymrcp.rtp.RtpPacket;
import com.cfsl.easymrcp.tts.RingBuffer;
import com.cfsl.easymrcp.utils.ReSample;
import com.cfsl.easymrcp.vad.VadHandle;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.cfsl.easymrcp.rtp.RtpPacket.parseRtpHeader;

@Data
@Slf4j
public abstract class AsrHandler implements RtpConnection {
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
    
    // 保存Speech-Complete-Timeout参数值
    private Long speechCompleteTimeout;

    private DatagramSocket socket;
    private int RTP_PORT; // RTP端口
    private static final int BUFFER_SIZE = 172;
    byte[] buffer = new byte[BUFFER_SIZE];
    protected Boolean stop = false;
    protected CountDownLatch countDownLatch = new CountDownLatch(1);
    VadHandle vadHandle;

    public void setConfig(AsrConfig asrConfig) {
        if (asrConfig.getIdentifyPatterns() != null && !asrConfig.getIdentifyPatterns().isEmpty()) {
            this.identifyPatterns = asrConfig.getIdentifyPatterns();
        }
        if (asrConfig.getReSample() != null && !asrConfig.getReSample().isEmpty()) {
            this.reSample = asrConfig.getReSample();
        }
    }

    @Override
    public void create(String localIp, DatagramSocket localSocket, String remoteIp, int remotePort) {
        this.socket = localSocket;
        this.RTP_PORT = localSocket.getLocalPort();
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

    public abstract void create();
    
    /**
     * TODO vad检测时间需要重新设计，目前新参数会导致重新加载vad
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

    public boolean receive() {
        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
            // 使用Speech-Complete-Timeout参数初始化VAD
            vadHandle = speechCompleteTimeout != null ? 
                    new VadHandle(speechCompleteTimeout) : new VadHandle();
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                RingBuffer inputRingBuffer = new RingBuffer(1000000);
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet); // 阻塞等待数据
                    } catch (IOException e) {
                        if (e.getMessage().equalsIgnoreCase("socket closed") || e instanceof SocketTimeoutException) {
                            log.info("rtp socket {} port closed", RTP_PORT);
                        } else {
                            log.error(e.getMessage(), e);
                        }
                        return;
                    }
                    byte[] rtpData = packet.getData();
                    int packetLength = packet.getLength();
                    // 解析RTP头部（前12字节）
                    RtpPacket parsedPacket = parseRtpHeader(rtpData, packetLength);
                    // 提取G.711a负载
                    byte[] g711Data = parsedPacket.getPayload();
                    // G.711a解码为PCM
                    byte[] pcmData = G711AUtil.decode(g711Data);
                    if (reSample != null && reSample.equals("upsample8kTo16k")) {
                        byte[] bytes = ReSample.resampleFrame(pcmData);
                        inputRingBuffer.put(bytes);
                    } else {
                        inputRingBuffer.put(pcmData);
                    }
                    if (inputRingBuffer.getAvailable() >= 2048) {
                        byte[] take = inputRingBuffer.take(2048);
                        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
                            // 获取前一个说话状态
                            boolean isSpeakingBefore = vadHandle.getIsSpeaking();
                            // VAD处理PCM数据
                            vadHandle.receivePcm(take);
                            // 获取当前说话状态
                            boolean isSpeakingNow = vadHandle.getIsSpeaking();
                            if (isSpeakingNow) {
                                if (!isSpeakingBefore) {
                                    reCreate();
                                }
                                receive(take);
                            } else if (isSpeakingBefore) {
                                log.info("VAD detected speech end, sending EOF");
                                sendEof();
                            }
                        } else {
                            receive(take);
                        }
                    }
                }
            }
        }).start();
        return true;
    }

    @Override
    public void close() {
        socket.close();
        asrClose();
        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
            vadHandle.release();
        }
        // 取消所有超时定时器
        cancelTimeouts();
//        getCallback().apply("");
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
