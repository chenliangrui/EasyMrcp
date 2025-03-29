package com.example.easymrcp.tts;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;

public class RealTimeAudioProcessor {
    // 音频参数
    private static final int SOURCE_RATE = 24000;
    private static final int TARGET_RATE = 8000;
    private static final double SAMPLE_RATIO = (double) SOURCE_RATE / TARGET_RATE;
    private static final int PCM_SAMPLE_SIZE = 2;
    private static final int FRAME_DURATION_MS = 20;
    private static final int SAMPLES_PER_FRAME = TARGET_RATE * FRAME_DURATION_MS / 1000; // 160

    // RTP参数
    private static final int RTP_HEADER_SIZE = 12;
    private static final int RTP_PAYLOAD_TYPE = 0; // PCMU=0
    private static final int RTP_VERSION = 2;

    // FIR抗混叠滤波器参数（截止频率3400Hz）
    private static final double[] FIR_COEFFS = {
            0.0013, 0.0029, 0.0062, 0.0112, 0.0173, 0.0238, 0.0295, 0.0330,
            0.0330, 0.0295, 0.0238, 0.0173, 0.0112, 0.0062, 0.0029, 0.0013
    };

    // 网络参数
    public String DEST_IP;
    public int DEST_PORT;

    // 线程间缓冲队列
    private final ArrayBlockingQueue<byte[]> inputQueue = new ArrayBlockingQueue<>(10000);
    private final ArrayBlockingQueue<byte[]> outputQueue = new ArrayBlockingQueue<>(10000);

    // RTP状态
    private int sequenceNumber = 0;
    private int timestamp = 0;

    /**
     * 初始化音频采集
     */
    public void initAudioCapture() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(SOURCE_RATE, 16, 1, true, false);
        TargetDataLine line = AudioSystem.getTargetDataLine(format);
        line.open(format, 480 * 10); // 10ms缓冲
        line.start();

        new Thread(() -> {
            byte[] buffer = new byte[480]; // 240样本*2字节
            while (true) {
                int read = line.read(buffer, 0, buffer.length);
                if (read > 0) inputQueue.add(buffer.clone());
            }
        }).start();
    }

    /**
     * 初始化音频采集
     */
    public void putData(byte[] pcmBuffer, int bytesRead) throws LineUnavailableException {
        byte[] b = new byte[bytesRead];
//        inputQueue.add(pcmBuffer.clone());
        System.arraycopy(pcmBuffer, 0, b, 0, bytesRead);
        inputQueue.add(b);
    }

    /**
     * 实时处理线程
     */
    //TODO 使用环形缓冲解决降采样问题,不使用队列
    public void startProcessing() {
        ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            //播放测试
            RealtimePCMPlayer player = new RealtimePCMPlayer();
            try {
                player.init();
            } catch (LineUnavailableException e) {
                throw new RuntimeException(e);
            }
            player.play(audioBuffer.toByteArray());
        }).start();
        //实时处理
        new Thread(() -> {
            double[] filterHistory = new double[FIR_COEFFS.length];
            while (true) {
                try {
                    byte[] pcm24k = inputQueue.take();
                    audioBuffer.write(pcm24k, 0, pcm24k.length);
                    System.out.println("pcm24k size: " + pcm24k.length);
                    ByteBuffer inputBuffer = ByteBuffer.wrap(pcm24k)
                            .order(ByteOrder.LITTLE_ENDIAN);

                    // 降采样与滤波
                    ByteBuffer outputBuffer = ByteBuffer.allocate(SAMPLES_PER_FRAME * PCM_SAMPLE_SIZE)
                            .order(ByteOrder.LITTLE_ENDIAN);

                    for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
                        double srcPos = i * SAMPLE_RATIO;
                        int basePos = (int) srcPos;
                        double fraction = srcPos - basePos;
                        int baseIndex = (int) srcPos;

                        // 关键：添加边界检查
                        if (baseIndex < 0 || (baseIndex + 1) * 2 > inputBuffer.limit()) {
                            break; // 或填充默认值
                        }

                        // 线性插值
                        short prev = inputBuffer.getShort(basePos * 2);
                        short next = inputBuffer.getShort((basePos + 1) * 2);
                        double interpolated = prev * (1 - fraction) + next * fraction;

                        // FIR滤波
                        System.arraycopy(filterHistory, 1, filterHistory, 0, filterHistory.length - 1);
                        filterHistory[filterHistory.length - 1] = interpolated;

                        double filtered = 0;
                        for (int j = 0; j < FIR_COEFFS.length; j++) {
                            filtered += filterHistory[j] * FIR_COEFFS[j];
                        }

                        outputBuffer.putShort((short) filtered);
                    }

                    // G711编码
                    byte[] pcm8k = outputBuffer.array();
                    byte[] g711Data = encodePcmToG711U(pcm8k);
                    outputQueue.add(g711Data);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * RTP发送线程
     */
    public void startRtpSender() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(DEST_IP);

        new Thread(() -> {
            while (true) {
                try {
                    byte[] payload = outputQueue.take();
                    byte[] rtpPacket = buildRtpPacket(payload);

                    DatagramPacket packet = new DatagramPacket(
                            rtpPacket, rtpPacket.length, address, DEST_PORT
                    );
                    socket.send(packet);

                    // 更新RTP状态
                    sequenceNumber = (sequenceNumber + 1) % 65536;
                    timestamp += SAMPLES_PER_FRAME;

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * G711编码核心
     */
    private byte[] encodePcmToG711U(byte[] pcmData) {
        ByteBuffer buffer = ByteBuffer.wrap(pcmData)
                .order(ByteOrder.LITTLE_ENDIAN);
        byte[] g711Data = new byte[pcmData.length / 2];

        for (int i = 0; i < g711Data.length; i++) {
            short sample = buffer.getShort();
            g711Data[i] = linearToUlaw(sample);
        }
        return g711Data;
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

    /**
     * 构造RTP包
     */
    private byte[] buildRtpPacket(byte[] payload) {
        ByteBuffer header = ByteBuffer.allocate(RTP_HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .put((byte) ((RTP_VERSION << 6) | 0x00)) // 版本+填充位
                .put((byte) (RTP_PAYLOAD_TYPE & 0x7F))  // 负载类型
                .putShort((short) sequenceNumber)        // 序列号
                .putInt(timestamp)                      // 时间戳
                .putInt(0x12345678);                    // SSRC

        return ByteBuffer.allocate(RTP_HEADER_SIZE + payload.length)
                .put(header.array())
                .put(payload)
                .array();
    }

    public static void main(String[] args) throws Exception {
        RealTimeAudioProcessor processor = new RealTimeAudioProcessor();
        processor.initAudioCapture();
        processor.startProcessing();
        processor.startRtpSender();
    }
}