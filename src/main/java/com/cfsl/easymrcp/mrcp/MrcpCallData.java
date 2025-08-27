package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.tcp.MrcpEventWithCallback;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.LinkedBlockingQueue;
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

    /**
     * 是否可以打断
     * 某些tts可以设置为不可打断
     */
    @Getter
    private AtomicBoolean interruptEnable = new AtomicBoolean(true);

    @Getter
    @Setter
    AsrHandler asrHandler;

    @Getter
    @Setter
    TtsHandler ttsHandler;

    /**
     * 多次串行speak的队列
     */
    @Getter
    LinkedBlockingQueue<MrcpEventWithCallback> mrcpEventQueue = new LinkedBlockingQueue<>();

    public void setSpeaking(Boolean speaking) {
        this.speaking.set(speaking);
    }

    public boolean isInterruptEnable() {
        return this.interruptEnable.get();
    }

    public void setInterruptEnable(Boolean interruptEnable) {
        this.interruptEnable.set(interruptEnable);
    }

    public boolean isSpeaking() {
        return speaking.get();
    }
}
