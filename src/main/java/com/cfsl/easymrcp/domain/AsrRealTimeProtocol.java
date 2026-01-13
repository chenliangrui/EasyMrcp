package com.cfsl.easymrcp.domain;

import lombok.Data;

/**
 * 通过与easymrcp客户端连接的tcp实时发送asr识别结果
 */
@Data
public class AsrRealTimeProtocol {

    /**
     * 使用的asr引擎名称
     */
    private String asrEngine;

    /**
     * 实时asr识别结果
     */
    private String asrResult;
}
