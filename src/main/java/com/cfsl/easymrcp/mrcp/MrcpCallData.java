package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mrcp业务层面数据
 */
public class MrcpCallData {
    /**
     * pbx的通话呼叫uuid
     * 作为mrcp的通话唯一id
     */
    @Setter
    private String callId;

    /**
     * 是否正在tts
     * 由EasyMrcp内部进行语音打断
     */
    private AtomicBoolean speaking = new AtomicBoolean(false);

    @Getter
    @Setter
    AsrHandler asrHandler;

    @Getter
    @Setter
    TtsHandler ttsHandler;

//    @Getter
//    @Setter
//    RealTimeAudioProcessor realTimeAudioProcessor;

    public void setSpeaking(Boolean speaking) {
        this.speaking.set(speaking);
    }

    public boolean isSpeaking() {
        return speaking.get();
    }
}
