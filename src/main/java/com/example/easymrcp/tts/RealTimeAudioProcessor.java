package com.example.easymrcp.tts;

import com.example.easymrcp.asr.FunAsrProcessor;
import com.example.easymrcp.rtp.G711uDecoder;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

@Slf4j
public class RealTimeAudioProcessor {
    // 网络参数
    public String DEST_IP;
    public int DEST_PORT;

    // 线程间缓冲队列
    private final RingBuffer inputRingBuffer = new RingBuffer(1000000);
    private final ArrayBlockingQueue<byte[]> outputQueue = new ArrayBlockingQueue<>(10000);

    private boolean stop;

    /**
     * 初始化音频采集
     */
    public void putData(byte[] pcmBuffer, int bytesRead) throws LineUnavailableException {
        byte[] b = new byte[bytesRead];
//        inputQueue.add(pcmBuffer.clone());
        System.arraycopy(pcmBuffer, 0, b, 0, bytesRead);
        inputRingBuffer.put(b);
    }

    /**
     * 实时处理线程
     */
    //TODO 使用环形缓冲解决降采样问题,不使用队列
    public void startProcessing() {
        //实时处理
        new Thread(() -> {
            while (true) {
                try {
                    byte[] pcm24k;
                    while ((pcm24k = inputRingBuffer.take(102400)) == null) {
                        Thread.sleep(200); // 非阻塞等待
                        log.info("inputRingBuffer available: {}", inputRingBuffer.getAvailable());
                        if (stop) {
                            return;
                        }
                    }
                    byte[] bytes = downsample24kTo8k(pcm24k);
//                    // G711编码
                    byte[] bytes1 = G711UEncoder.encode(bytes);
                    outputQueue.add(bytes1);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }).start();
    }

    public static byte[] downsample24kTo8k(byte[] inputData) {
        int sampleRateRatio = 3; // 24k/8k=3

        // 1. 调整输入长度为偶数值，丢弃不完整的采样点
        int adjustedInputLength = inputData.length & 0xFFFFFFFE; // 等价于 inputLength - (inputLength % 2)

        // 2. 计算输出数据长度（向下取整）
        int inputSampleCount = adjustedInputLength / 2;
        System.out.println("inputSampleCount: " + inputSampleCount);
        int outputSampleCount = inputSampleCount / sampleRateRatio;
        int outputLength = outputSampleCount * 2;
        byte[] outputData = new byte[outputLength];

        int outputIndex = 0;
        int sum = 0;
        int count = 0;

        // 3. 安全循环：确保i+1不超过数组长度
        for (int i = 0; i < adjustedInputLength; i += 2) {
            short sample = (short) (((inputData[i + 1] & 0xFF) << 8) | (inputData[i] & 0xFF));
            sum += sample;
            count++;

            // 每累积3个采样点，计算均值并写入输出
            if (count == sampleRateRatio) {
                short avg = (short) (sum / sampleRateRatio);
                outputData[outputIndex++] = (byte) (avg & 0xFF);
                outputData[outputIndex++] = (byte) ((avg >> 8) & 0xFF);
                sum = 0;
                count = 0;
            }
        }
        return outputData;
    }

    /**
     * RTP发送线程
     */
    public void startRtpSender() throws Exception {
        new Thread(() -> {
            G711RtpSender sender = null;
            try {
                sender = new G711RtpSender(DEST_IP, DEST_PORT);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            while (true) {
                try {
                    byte[] payload = outputQueue.take();
                    sender.sendFrame(payload);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
//            Thread.sleep(FRAME_DURATION); // 控制发送速率
            }
        }).start();
    }

    /**
     * PCM转μ-law算法
     */
    private byte linearToUlaw(short sample) {
        int sign = (sample & 0x8000) >> 8;
        if (sign != 0) sample = (short) -sample;
        sample = (short) Math.min(sample + 132, 32767);

        int exp = 7;
        for (; (sample & 0x4000) == 0 && exp > 0; exp--, sample <<= 1) ;
        int mant = (sample >> 4) & 0x0F;
        return (byte) ~(sign | (exp << 4) | mant);
    }

    public void stopRtpSender() {
        this.stop = true;
    }
}