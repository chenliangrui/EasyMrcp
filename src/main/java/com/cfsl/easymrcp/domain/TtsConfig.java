package com.cfsl.easymrcp.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TtsConfig extends BaseConfig{
    // tts发音人名称
    public String voice;

    /**
     * 首包跳过字节数
     * 由于tts的语音开始有一小段时间的无音频，所以需要跳过的开头的字节(PCM格式)
     * 例如使用：PCM 8kHz 16bit，那么跳过0.1秒音频就是：8000 * 2 * 0.1 = 1600字节
     * 注意：该值是理论最大值，实际使用中可能由于首包小于设定字节数而变小。
     */
    public int skipBytesInTheFirstPacket;

    /**
     * 尾包跳过字节数
     * 由于tts的语音结尾有一小段时间的无音频，所以需要跳过的末尾的字节(PCM格式)
     * 例如使用：PCM 8kHz 16bit，那么跳过0.1秒音频就是：8000 * 2 * 0.1 = 1600字节
     * 注意：该值是理论最大值，当前系统中的默认配置无法超过3000字节。并且实际使用中可能由于尾包小于设定字节数而变小。
     */
    public int skipBytesInTheEndPacket;
}
