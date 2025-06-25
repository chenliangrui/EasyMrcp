package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.TtsCallback;
import com.cfsl.easymrcp.rtp.G711AUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.LineUnavailableException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RealTimeAudioProcessor {
    private G711RtpSender sender;
    @Setter
    private int localPort;
    // 网络参数
    public String destinationIp;
    public int destinationPort;
    @Setter
    private TtsCallback callback;
    private String reSample;
    // 为解决24khz采样率重采样到8kHz采样率时数据长度不为6的倍数时导致的偶发的噪声问题，统一读取6 * n字节
    private int receiveTakeBytes = 6 * 500;

//    FileOutputStream fileOutputStream;
//
//    {
//        try {
//            fileOutputStream = new FileOutputStream("D:\\code\\EasyMrcp\\src\\main\\resources\\test.pcm", true);
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public RealTimeAudioProcessor(DatagramSocket socket, String reSample, String remoteIp, int remotePort) {
        this.reSample = reSample;
        this.destinationIp = remoteIp;
        this.destinationPort = remotePort;

        try {
            sender = new G711RtpSender(socket, destinationIp, destinationPort);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    // 线程间缓冲队列
    private final RingBuffer inputRingBuffer = new RingBuffer(1000000);
    private final RingBuffer outputQueue = new RingBuffer(1000000);

    private boolean stop;

    /**
     * 初始化音频采集
     */
    public void putData(byte[] pcmBuffer, int bytesRead) throws LineUnavailableException {
        byte[] b = new byte[bytesRead];
        System.arraycopy(pcmBuffer, 0, b, 0, bytesRead);
        inputRingBuffer.put(b);
    }

    /**
     * 实时处理线程
     */
    public void startProcessing() {
        //实时处理
        new Thread(() -> {
            while (true) {
                try {
                    byte[] pcm;
                    // 为解决24khz采样率重采样到8kHz采样率时数据长度不为6的倍数时导致的偶发的噪声问题，统一读取6 * n字节
                    while (inputRingBuffer.getAvailable() == 0 || inputRingBuffer.getAvailable() < receiveTakeBytes) {
//                        log.info("inputRingBuffer available: {}", inputRingBuffer.getAvailable());
                        // 判断是否含有结束标志，有的话取出最后的音频数据
                        if (inputRingBuffer.getAvailable() > 0) {
                            byte[] peek = inputRingBuffer.peek(receiveTakeBytes);
                            if (peek[peek.length - 2] == TTSConstant.TTS_END_BYTE && peek[peek.length - 1] == TTSConstant.TTS_END_BYTE) {
                                break;
                            }
                        }
                        Thread.sleep(200); // 非阻塞等待
                        if (stop) {
                            return;
                        }
                    }
                    pcm = inputRingBuffer.take(receiveTakeBytes);
                    byte[] bytes;
                    if (reSample != null && reSample.equals("downsample24kTo8k")) {
                        bytes = downsample24kTo8k(pcm);
                    } else {
                        bytes = pcm;
                    }
//                    try {
//                        fileOutputStream.write(bytes);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
                    // G711编码
//                    byte[] g711uBytes = G711UEncoder.encode(bytes);
                    byte[] g711uBytes = G711AUtil.encode(bytes);
                    outputQueue.put(g711uBytes);
                    if (pcm[pcm.length - 2] == TTSConstant.TTS_END_BYTE && pcm[pcm.length - 1] == TTSConstant.TTS_END_BYTE) {
                        // 结束
                        outputQueue.put(TTSConstant.TTS_END_FLAG); // 结束标记
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }).start();
    }

    // 将 PCM 字节流从 24kHz 降采样到 8kHz
    public static byte[] downsample24kTo8k(byte[] inputBytes) {
        int sampleSize = 2; // 每个采样点 16-bit，即 2 字节
        int ratio = 3;      // 24kHz -> 8kHz，每3个采样点降为1个
        int totalSamples = inputBytes.length / sampleSize;
        int newSamples = totalSamples / ratio;
//        if (inputBytes.length % (2 * 3) != 0) {
//            log.info("输入数据长度不是采样帧3的倍数，可能导致噪声");
//        }

        byte[] outputBytes = new byte[newSamples * sampleSize];

        for (int i = 0; i < newSamples; i++) {
            int idx1 = i * ratio * sampleSize;
            int idx2 = idx1 + sampleSize;
            int idx3 = idx2 + sampleSize;

            // 防止越界（尾部可能不足 3 个采样点）
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
            outputBytes[i * 2]     = (byte) (avg & 0xFF);
            outputBytes[i * 2 + 1] = (byte) ((avg >> 8) & 0xFF);
        }

        return outputBytes;
    }

    /**
     * RTP发送线程
     */
    public void startRtpSender() {
        G711RtpSender finalSender = sender;
        new Thread(() -> {
            while (true) {
                try {
                    // 控制每次分包是160字节 * n
                    byte[] peek = outputQueue.peek(EMConstant.VOIP_SAMPLES_PER_FRAME * 1000);
                    if (stop) {
                        finalSender.close();
                        return;
                    }
                    if (peek == null) {
                        continue;
                    }
                    int packageCount = peek.length / EMConstant.VOIP_SAMPLES_PER_FRAME;
                    int redundantData = peek.length % EMConstant.VOIP_SAMPLES_PER_FRAME;
                    if (!(peek[peek.length - 2] == TTSConstant.TTS_END_BYTE) && !(peek[peek.length - 1] == TTSConstant.TTS_END_BYTE) && redundantData != 0) {
                        if (packageCount > 1) {
                            packageCount = packageCount - 1;
                        } else {
                            packageCount = 1;
                        }
                    } else if (packageCount == 0) {
                        packageCount = 1;
                    }
                    byte[] payload = outputQueue.take(EMConstant.VOIP_SAMPLES_PER_FRAME * packageCount);
                    log.debug("send {} bytes", payload.length);
                    finalSender.sendFrame(payload);
                    if (payload[payload.length - 2] == TTSConstant.TTS_END_BYTE && payload[payload.length - 1] == TTSConstant.TTS_END_BYTE) {
                        stopRtpSender();
                        callback.apply(null);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }).start();
    }

    public void stopRtpSender() {
        this.stop = true;
//        try {
//            fileOutputStream.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }
}