package com.example.easymrcp.vad;

import lombok.Setter;

public class VadHandle {
    private static final int SAMPLE_SIZE_BITS = 16;     // 位深度
    // 分贝计算相关常量
    private static final double MAX_AMPLITUDE = Math.pow(2, SAMPLE_SIZE_BITS - 1); // 16bit最大振幅值32768[[5]](https://blog.csdn.net/weixin_39668199/article/details/111786701)
    private static final double REF_PRESSURE = 1.0;
    @Setter// 参考声压（可根据需求调整）
    private long lastSilence;
    /**
     * PCM分贝计算核心方法
     * @param pcmData PCM字节数组
     * @param length  有效数据长度
     * @return 分贝值
     */
    public double calculateDecibel(byte[] pcmData, int length) {
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
