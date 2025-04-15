package com.example.easymrcp.testutils;

import javax.sound.sampled.*;

public class RealTimePCMDecibelDetector {
    // PCM参数配置（需与输入设备一致）
    private static final float SAMPLE_RATE = 16000.0f; // 采样率
    private static final int SAMPLE_SIZE_BITS = 16;     // 位深度
    private static final int CHANNELS = 1;              // 单声道
    private static final boolean SIGNED = true;         // 有符号数据
    private static final boolean BIG_ENDIAN = false;     // 小端字节序

    // 分贝计算相关常量
    private static final double MAX_AMPLITUDE = Math.pow(2, SAMPLE_SIZE_BITS - 1); // 16bit最大振幅值32768[[5]](https://blog.csdn.net/weixin_39668199/article/details/111786701)
    private static final double REF_PRESSURE = 1.0;      // 参考声压（可根据需求调整）

    public static void main(String[] args) {
        try {
            // 1. 配置音频格式
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    SAMPLE_RATE,
                    SAMPLE_SIZE_BITS,
                    CHANNELS,
                    (SAMPLE_SIZE_BITS / 8) * CHANNELS,
                    SAMPLE_RATE,
                    BIG_ENDIAN
            );

            // 2. 打开音频输入流（麦克风）
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            // 3. 设置缓冲区大小（实时处理建议1024-4096）
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            // 4. 实时处理循环
            while (true) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    double db = calculateDecibel(buffer, bytesRead);
                    System.out.printf("当前分贝: %.2f dB\n", db);
                }
            }

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    /**
     * PCM分贝计算核心方法
     * @param pcmData PCM字节数组
     * @param length  有效数据长度
     * @return 分贝值
     */
    public static double calculateDecibel(byte[] pcmData, int length) {
        int sampleCount = length / 2; // 16bit=2字节，每个采样点占2字节[[3]](https://www.5axxw.com/questions/simple/dbeo64)
        double sum = 0.0;

        // 遍历所有采样点
        for (int i = 0; i < sampleCount; i++) {
            // 将两个字节组合成short（16bit有符号）
            short sample = (short) (
                    (pcmData[2*i + 1] << 8) |
                            (pcmData[2*i] & 0xFF)
            );

            // 计算振幅绝对值并累加[[8]](https://blog.csdn.net/balijinyi/article/details/80284520)
            sum += Math.abs(sample);
        }

        // 计算平均振幅并转换为分贝[[5]](https://blog.csdn.net/weixin_39668199/article/details/111786701)
        double avgAmplitude = sum / sampleCount;
        return 20 * Math.log10(avgAmplitude / MAX_AMPLITUDE * REF_PRESSURE);
    }
}
