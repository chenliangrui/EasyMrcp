package com.cfsl.easymrcp.common;

public class EMConstant {
    public static final int VOIP_SAMPLE_RATE = 8000;
    //每帧20ms
    public static final int VOIP_FRAME_DURATION = 20;
    //每帧数据大小
    public static final int VOIP_SAMPLES_PER_FRAME = VOIP_SAMPLE_RATE * VOIP_FRAME_DURATION / 1000;

    // TCP Server Constants
    public static final int DEFAULT_TCP_PORT = 9090;
    public static final String TCP_SERVER_PORT = "tcp.server.port";

    public static final String XFYUN = "xfyun";
    public static final String FUNASR = "funasr";
    public static final String TENCENT_CLOUD = "tencent-cloud";
    public static final String ALIYUN = "aliyun";
    public static final String KOKORO = "kokoro";
    public static final String EXAMPLE_ASR = "example-asr";
    public static final String EXAMPLE_TTS = "example-tts";
}
