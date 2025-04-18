package com.example.easymrcp.asr;

import com.example.easymrcp.domain.AsrConfig;
import com.example.easymrcp.mrcp.AsrCallback;
import com.example.easymrcp.rtp.G711uDecoder;
import com.example.easymrcp.rtp.RtpConnection;
import com.example.easymrcp.rtp.RtpPacket;
import com.example.easymrcp.tts.RingBuffer;
import com.example.easymrcp.vad.VadHandle;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.example.easymrcp.rtp.RtpPacket.parseRtpHeader;

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
                            if (e.getMessage().equals("socket closed")) {
                                log.info("rtp socket {} port closed", RTP_PORT);
                            } else {
                                log.error(e.getMessage(), e);
                            }
                            return;
                        }
                        byte[] rtpData = packet.getData();
                        int packetLength = packet.getLength();
//                        System.out.println("RTP数据包长度：" + packetLength);
                        // 解析RTP头部（前12字节）
                        RtpPacket parsedPacket = parseRtpHeader(rtpData, packetLength);
                        // 提取G.711u负载
                        byte[] g711Data = parsedPacket.getPayload();
                        // G.711u解码为PCM
                        byte[] pcmData = G711uDecoder.decodeG711U(g711Data);
                        inputRingBuffer.put(pcmData);
//                        byte[] bytes = ReSample.resampleFrame(pcmData);
//                        try {
//                            fileOutputStream.write(bytes);
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
                        if (inputRingBuffer.getAvailable() >= 2048) {
                            byte[] take = inputRingBuffer.take(2048);
                            if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
                                Boolean isSpeakingBefore = vadHandle.getIsSpeaking();
                                vadHandle.receivePcm(take);
                                if (vadHandle.getIsSpeaking()) {
                                    if (!isSpeakingBefore) {
                                        reCreate();
                                    }
                                    receive(take);
                                } else {
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
