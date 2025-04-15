package com.example.easymrcp.asr;

import com.example.easymrcp.mrcp.AsrCallback;
import com.example.easymrcp.rtp.G711uDecoder;
import com.example.easymrcp.rtp.RtpConnection;
import com.example.easymrcp.rtp.RtpPacket;
import com.example.easymrcp.testutils.RealTimePCMDecibelDetector;
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
    @Setter
    protected String identifyPatterns;

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
                        needVad(pcmData, vadHandle);
//                        byte[] bytes = ReSample.resampleFrame(pcmData);
//                        try {
//                            fileOutputStream.write(bytes);
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
                        receive(pcmData);
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
//        try {
//            fileOutputStream.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }

    public abstract void receive(byte[] pcmData);

    public abstract void asrClose();

    //TODO 语音识别时，需要VAD
    public void needVad(byte[] pcmData, VadHandle vadHandle) {
        if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(identifyPatterns)) {
            double v = vadHandle.calculateDecibel(pcmData, pcmData.length);
            if (v > ASRConstant.VAD_THRESHOLD) {
                // 检测出开始说话
                log.info("vad threshold: {}", v);
//                reCreate();
            } else {
                vadHandle.setLastSilence(System.currentTimeMillis());
            }
        }
    }

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
