package com.example.easymrcp.tts;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

public class RealtimePCMPlayer {

    private static final float SAMPLE_RATE = 24000.0f;  // 8kHz采样率
    private static final int SAMPLE_SIZE_BITS = 16;     // 16位深度
    private static final int CHANNELS = 1;              // 单声道
    private static final boolean SIGNED = true;          // 有符号采样
    private static final boolean BIG_ENDIAN = false;     // 小端字节序

    private SourceDataLine line;

    // 初始化音频播放设备
    public void init() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                (SAMPLE_SIZE_BITS / 8) * CHANNELS,
                SAMPLE_RATE,
                BIG_ENDIAN
        );

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format, 4096);
        line.start();
    }

    // 播放PCM数据
    public void play(byte[] pcmData) {
        line.write(pcmData, 0, pcmData.length);
    }

    // 停止并释放资源
    public void stop() {
        line.drain();
        line.stop();
        line.close();
    }

    public static void main(String[] args) {
        try {
            RealtimePCMPlayer player = new RealtimePCMPlayer();
            player.init();

            // 示例：生成1秒的800Hz正弦波测试音频
            int duration = 1; // 秒
            int bufferSize = (int) (SAMPLE_RATE * duration);
            byte[] testData = new byte[bufferSize * 2]; // 16位=2字节

            // 生成测试音频数据（可替换为真实数据源）
            double frequency = 800.0; // 频率
            for (int i = 0; i < bufferSize; i++) {
                double angle = (i / SAMPLE_RATE) * frequency * 2.0 * Math.PI;
                short sample = (short) (Short.MAX_VALUE * Math.sin(angle));
                testData[2*i] = (byte) (sample & 0xFF);
                testData[2*i + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            // 播放测试音频
            player.play(testData);
            Thread.sleep(duration * 1000); // 等待播放完成
            player.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void test(byte[] pcmBuffer) {
        //播放测试
        RealtimePCMPlayer player = new RealtimePCMPlayer();
        try {
            player.init();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        player.play(pcmBuffer);
    }
}