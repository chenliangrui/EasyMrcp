package com.cfsl.easymrcp.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AsrConfig extends BaseConfig {
    /**
     * !!必须配置!!
     * 配置asr语音识别模式: dictation(一句话语音识别) 或者 transliterate(长时间语音转写)
     */
    protected String identifyPatterns;
}
