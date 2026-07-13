package com.cfsl.easymrcp.vad;

import ai.onnxruntime.OrtException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Silero VAD检测器，额外增加能量阈值过滤。
 * VAD模型负责判断“像不像人声”，能量阈值负责过滤低能量底噪。
 *
 * @author VvvvvGH
 */
@Slf4j
public class SlieroVadDetector {
    // 实时统计日志打印间隔，避免每个音频帧都刷日志
    private static final long REALTIME_STATS_LOG_INTERVAL_MS = 200L;
    // Silero VAD ONNX模型，用于输出当前音频帧的人声概率
    private final SlieroVadOnnxModel model;
    // VAD语音开始阈值：speechProb >= startThreshold 时，模型侧认为可能开始说话
    private final float startThreshold;
    // VAD语音结束阈值：已触发说话后，speechProb < endThreshold 时进入可能结束判断
    private final float endThreshold;
    // 音频采样率，只支持8000或16000
    private final int samplingRate;
    // 判定语音结束需要持续静音的采样点数
    private final float minSilenceSamples;
    // 语音起止点前后补偿的采样点数
    private final float speechPadSamples;
    // 能量阈值最小下限：energyThreshold不会低于该值，避免安静环境下阈值过低
    private final float minEnergyThreshold;
    // 动态能量阈值倍数：energyThreshold = max(minEnergyThreshold, noiseFloorEnergy * energyThresholdMultiplier)
    private final float energyThresholdMultiplier;
    // 底噪更新的指数滑动平均系数；值越大，noiseFloorEnergy跟随当前rmsEnergy越快
    private final float noiseFloorAlpha = 0.01f;
    // 估计的背景底噪能量；无人说话时，由历史rmsEnergy平滑更新得到
    private float noiseFloorEnergy;
    // 当前动态能量阈值；最终能量过滤条件是 rmsEnergy >= energyThreshold
    private float energyThreshold;
    // 当前是否已经进入“正在说话”状态
    private boolean triggered;
    // 临时语音结束采样点，用于等待持续静音确认
    private int tempEnd;
    // 当前累计处理到的采样点位置
    private int currentSample;
    // 复用的音频转换缓冲区，避免每帧重复分配
    private float[] audioBuffer;
    // 复用的检测结果Map，避免频繁分配
    private final Map<String, Double> resultMap;
    // 上一次打印实时统计日志的时间戳
    private long lastRealtimeStatsLogMs;

    public SlieroVadDetector(String modelPath,
                             float startThreshold,
                             float endThreshold,
                             int samplingRate,
                             int minSilenceDurationMs,
                             int speechPadMs) throws OrtException {
        this(modelPath, startThreshold, endThreshold, samplingRate, minSilenceDurationMs, speechPadMs, 0.01f, 1.4f);
    }

    public SlieroVadDetector(String modelPath,
                             float startThreshold,
                             float endThreshold,
                             int samplingRate,
                             int minSilenceDurationMs,
                             int speechPadMs,
                             float energyThreshold) throws OrtException {
        this(modelPath, startThreshold, endThreshold, samplingRate, minSilenceDurationMs, speechPadMs, energyThreshold, 1.4f);
    }

    public SlieroVadDetector(String modelPath,
                             float startThreshold,
                             float endThreshold,
                             int samplingRate,
                             int minSilenceDurationMs,
                             int speechPadMs,
                             float energyThreshold,
                             float energyThresholdMultiplier) throws OrtException {
        // 校验采样率
        if (samplingRate != 8000 && samplingRate != 16000) {
            throw new IllegalArgumentException("Does not support sampling rates other than [8000, 16000]");
        }

        // 初始化参数
        this.model = new SlieroVadOnnxModel(modelPath);
        this.startThreshold = startThreshold;
        this.endThreshold = endThreshold;
        this.samplingRate = samplingRate;
        this.minSilenceSamples = samplingRate * minSilenceDurationMs / 1000f;
        this.speechPadSamples = samplingRate * speechPadMs / 1000f;
        this.minEnergyThreshold = energyThreshold;
        this.energyThresholdMultiplier = energyThresholdMultiplier;
        this.resultMap = new HashMap<>(3);
        // 初始化检测状态
        reset();
    }

    /**
     * 重置检测器状态
     */
    public void reset() {
        model.resetStates();
        triggered = false;
        tempEnd = 0;
        currentSample = 0;
        audioBuffer = null;
        noiseFloorEnergy = minEnergyThreshold;
        energyThreshold = minEnergyThreshold;
        lastRealtimeStatsLogMs = 0L;
    }

    /**
     * 计算当前音频帧的RMS能量。
     * rmsEnergy只表示当前帧音量强度，不是动态阈值。
     *
     * @param audioData 音频采样数据
     * @param length 实际参与计算的采样长度
     * @return 当前帧RMS能量
     */
    private float calculateRMSEnergy(float[] audioData, int length) {
        float sum = 0.0f;
        for (int i = 0; i < length; i++) {
            float sample = audioData[i];
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / length);
    }

    private void updateNoiseFloorIfNeeded(float rmsEnergy) {
        if (!triggered) {
            // 只有未进入说话状态时，才把当前帧能量纳入底噪估计。
            // 这里用平滑更新，避免单帧突增直接把底噪抬得过高。
            noiseFloorEnergy = noiseFloorAlpha * rmsEnergy + (1 - noiseFloorAlpha) * noiseFloorEnergy;
        }
        // 动态能量阈值由底噪乘以倍数得到，但不会低于最小下限。
        // 后续真正判断能量是否通过时，用的是当前帧 rmsEnergy >= energyThreshold。
        energyThreshold = Math.max(minEnergyThreshold, noiseFloorEnergy * energyThresholdMultiplier);
    }

    private boolean shouldPrintRealtimeStats(long nowMillis) {
        if (lastRealtimeStatsLogMs != 0L && nowMillis - lastRealtimeStatsLogMs < REALTIME_STATS_LOG_INTERVAL_MS) {
            return false;
        }
        lastRealtimeStatsLogMs = nowMillis;
        return true;
    }

    private void logRealtimeStats(float speechProb, float rmsEnergy) {
        long nowMillis = System.currentTimeMillis();
        if (!shouldPrintRealtimeStats(nowMillis)) {
            return;
        }
        log.info("VAD realtime stats: vadProb={}, startThreshold={}, endThreshold={}, rmsEnergy={}, energyThreshold={}, noiseFloorEnergy={}, triggered={}",
                formatRealtimeStat(speechProb),
                formatRealtimeStat(startThreshold),
                formatRealtimeStat(endThreshold),
                formatRealtimeStat(rmsEnergy),
                formatRealtimeStat(energyThreshold),
                formatRealtimeStat(noiseFloorEnergy),
                triggered);
    }

    private String formatRealtimeStat(float value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    /**
     * 处理音频数据并检测语音事件。
     *
     * @param data PCM音频字节数组
     * @param returnSeconds 是否用秒返回起止时间；false时返回采样点位置
     * @return 检测到语音开始/结束事件时返回事件Map，否则返回空Map
     */
    public Map<String, Double> apply(byte[] data, boolean returnSeconds) {

        int numSamples = data.length / 2;

        // 尽量复用缓冲区
        if (audioBuffer == null || audioBuffer.length != numSamples) {
            audioBuffer = new float[numSamples];
        }

        // 将16位小端PCM转换为float采样值
        for (int i = 0; i < numSamples; i++) {
            audioBuffer[i] = ((data[i * 2] & 0xff) | (data[i * 2 + 1] << 8)) / 32767.0f;
        }

        // 当前处理窗口的采样点数量
        int windowSizeSamples = numSamples;
        // 更新当前累计采样点位置
        currentSample += windowSizeSamples;

        // 当前帧音频能量，后续会拿它和energyThreshold比较
        float rmsEnergy = calculateRMSEnergy(audioBuffer, numSamples);

        // VAD模型输出的人声概率
        float speechProb = 0;
        try {
            speechProb = model.call(new float[][]{audioBuffer}, samplingRate)[0];
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        updateNoiseFloorIfNeeded(rmsEnergy);
        logRealtimeStats(speechProb, rmsEnergy);

        // 人声概率重新超过开始阈值时，取消之前的临时结束点
        if (speechProb >= startThreshold && tempEnd != 0) {
            tempEnd = 0;
        }

        // 检测语音开始：VAD概率先过线，再检查当前帧能量是否超过动态能量阈值
        if (speechProb >= startThreshold && !triggered) {
            // 最终能量过滤条件：当前帧 rmsEnergy >= 当前动态阈值 energyThreshold
            if (rmsEnergy >= energyThreshold) {
                triggered = true;
                int speechStart = (int) (currentSample - speechPadSamples);
                speechStart = Math.max(speechStart, 0);

                // 复用结果Map
                resultMap.clear();

                // 根据参数返回秒或采样点位置
                if (returnSeconds) {
                    double speechStartSeconds = speechStart / (double) samplingRate;
                    double roundedSpeechStart = Math.round(speechStartSeconds * 10.0) / 10.0;
                    resultMap.put("start", roundedSpeechStart);
                } else {
                    resultMap.put("start", (double) speechStart);
                }
                resultMap.put("probability", (double) speechProb);
                resultMap.put("energy", (double) rmsEnergy);
                return resultMap;
            } else {
                return Collections.emptyMap();
            }
        }

        // 检测语音结束
        if (speechProb < endThreshold && triggered) {
            // 记录或保持临时结束点
            if (tempEnd == 0) {
                tempEnd = currentSample;
            }
            // 等待静音持续时间达到阈值后，才确认语音结束
            if (currentSample - tempEnd < minSilenceSamples) {
                return Collections.emptyMap();
            } else {
                // 计算语音结束位置并重置说话状态
                int speechEnd = (int) (tempEnd + speechPadSamples);
                tempEnd = 0;
                triggered = false;

                // 复用结果Map
                resultMap.clear();

                if (returnSeconds) {
                    double speechEndSeconds = speechEnd / (double) samplingRate;
                    double roundedSpeechEnd = BigDecimal.valueOf(speechEndSeconds).setScale(1, RoundingMode.HALF_UP).doubleValue();
                    resultMap.put("end", roundedSpeechEnd);
                } else {
                    resultMap.put("end", (double) speechEnd);
                }
                resultMap.put("probability", (double) speechProb);
                resultMap.put("energy", (double) rmsEnergy);
                return resultMap;
            }
        }

        // 没有检测到起止事件
        return Collections.emptyMap();
    }

    public void close() throws OrtException {
        reset();
        model.close();
    }
}
