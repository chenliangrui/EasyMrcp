package com.example.easymrcp.tts;

import com.example.easymrcp.mrcp.TtsCallback;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.LineUnavailableException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RealTimeAudioProcessor {
    private int localPort;
    // 网络参数
    public String destinationIp;
    public int destinationPort;
    @Setter
    private TtsCallback callback;
    private String reSample;

    public RealTimeAudioProcessor(int localPort, String reSample) {
        this.localPort = localPort;
        this.reSample = reSample;
    }

    // 线程间缓冲队列
    private final RingBuffer inputRingBuffer = new RingBuffer(1000000);
    private final ArrayBlockingQueue<byte[]> outputQueue = new ArrayBlockingQueue<>(10000);

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
                    while ((pcm = inputRingBuffer.take(102400)) == null) {
                        Thread.sleep(200); // 非阻塞等待
//                        log.info("inputRingBuffer available: {}", inputRingBuffer.getAvailable());
                        if (stop) {
                            return;
                        }
                    }
                    byte[] bytes;
                    if (reSample != null && reSample.equals("downsample24kTo8k")) {
                         bytes = downsample24kTo8k(pcm);
                    } else {
                        bytes = pcm;
                    }
                    // G711编码
                    byte[] g711uBytes = G711UEncoder.encode(bytes);
                    outputQueue.add(g711uBytes);
                    if (pcm[pcm.length - 2] == TTSConstant.TTS_END_BYTE && pcm[pcm.length - 1] == TTSConstant.TTS_END_BYTE) {
                        // 结束
                        outputQueue.add(TTSConstant.TTS_END_FLAG); // 结束标记
                    }
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
    public void startRtpSender() {
        new Thread(() -> {
            G711RtpSender sender = null;
            try {
                sender = new G711RtpSender(localPort, destinationIp, destinationPort);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            while (true) {
                try {
                    byte[] payload = outputQueue.poll(10, TimeUnit.SECONDS);
                    if (stop) {
                        return;
                    }
                    if (payload == null) {
                        continue;
                    }
                    sender.sendFrame(payload);
                    if (payload[0] == TTSConstant.TTS_END_BYTE && payload[1] == TTSConstant.TTS_END_BYTE) {
                        stopRtpSender();
                        callback.apply(null);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
//            Thread.sleep(FRAME_DURATION); // 控制发送速率
            }
        }).start();
    }

    public void stopRtpSender() {
        this.stop = true;
    }
}