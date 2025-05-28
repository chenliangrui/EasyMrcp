package com.cfsl.easymrcp.asr;

import com.cfsl.easymrcp.domain.AsrConfig;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.rtp.G711AUtil;
import com.cfsl.easymrcp.rtp.G711uDecoder;
import com.cfsl.easymrcp.rtp.RtpConnection;
import com.cfsl.easymrcp.rtp.RtpPacket;
import com.cfsl.easymrcp.tts.RingBuffer;
import com.cfsl.easymrcp.utils.ReSample;
import com.cfsl.easymrcp.vad.VadHandle;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

    private DatagramSocket socket;
    private int RTP_PORT; // RTP端口
    private static final int BUFFER_SIZE = 172;
    byte[] buffer = new byte[BUFFER_SIZE];
    protected Boolean stop = false;
    protected CountDownLatch countDownLatch = new CountDownLatch(1);
    VadHandle vadHandle;
//    录音测试
//    FileOutputStream fileOutputStream;
//
//    {
//        try {
//            fileOutputStream = new FileOutputStream("D:\\code\\EasyMrcp\\src\\main\\resources\\test.pcm", true);
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public void setConfig(AsrConfig asrConfig) {
        if (asrConfig.getIdentifyPatterns() != null && !asrConfig.getIdentifyPatterns().isEmpty()) {
            this.identifyPatterns = asrConfig.getIdentifyPatterns();
        }
        if (asrConfig.getReSample() != null && !asrConfig.getReSample().isEmpty()) {
            this.reSample = asrConfig.getReSample();
        }
    }

    @Override
    public void create(String localIp, int localPort, String remoteIp, int remotePort) {
        this.RTP_PORT = localPort;
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

    public void receive() {
        try {
            socket = new DatagramSocket(RTP_PORT);
            // 超过10s没有收到数据包，抛出异常
            socket.setSoTimeout(10000);
            if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
                vadHandle = new VadHandle();
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
                            if (e.getMessage().equals("socket closed") || e instanceof SocketTimeoutException) {
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
//                        byte[] bytes = ReSample.resampleFrame(pcmData);
                        if (inputRingBuffer.getAvailable() >= 2048) {
                            byte[] take = inputRingBuffer.take(2048);
                            if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
                                Boolean isSpeakingBefore = vadHandle.getIsSpeaking();
                                vadHandle.receivePcm(take);
                                if (vadHandle.getIsSpeaking()) {
                                    if (!isSpeakingBefore) {
                                        reCreate();
                                    }
//                                    try {
//                                        fileOutputStream.write(take);
//                                    } catch (IOException e) {
//                                        throw new RuntimeException(e);
//                                    }
                                    receive(take);
                                } else if (isSpeakingBefore) {
                                    log.info("send eof");
                                    sendEof();
                                }
                            } else {
                                receive(take);
                            }
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ;

    @Override
    public void close() {
//        socket.close();
        asrClose();
        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
            vadHandle.release();
        }
//        try {
//            fileOutputStream.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }

    public abstract void receive(byte[] pcmData);

    public abstract void sendEof();

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
