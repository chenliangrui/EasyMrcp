package com.example.easymrcp.mrcp;

/**
 * tts中识别完成并且发送完rtp的回调
 * 因为sip、mrcp、tts合成过程是异步的，所以需要在完成tts合成并且发送完rtp包后手动调用回调函数返回识别结果，
 * 此时mrcp会发送MrcpEventName.SPEAK_COMPLETE事件，完成此次tts过程。
 */
public interface TtsCallback {
    /**
     * tts合成完且发送完rtp包后回调，执行后mrcp会发送MrcpEventName.SPEAK_COMPLETE事件
     * @param msg 无需填写，写入null即可
     */
    void apply(String msg);
}
