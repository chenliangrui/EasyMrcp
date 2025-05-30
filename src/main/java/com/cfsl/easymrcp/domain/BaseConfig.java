package com.cfsl.easymrcp.domain;

import lombok.Data;

@Data
public class BaseConfig {
    /**
     * 某些情况下获取的音频并不符合voip标准的8k采样率，所以需要重新采样
     */
    private String reSample;
}
