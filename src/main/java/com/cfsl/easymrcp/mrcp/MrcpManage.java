package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.tts.RealTimeAudioProcessor;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务层面全局管理mrcp通话
 */
@Slf4j
@Component
public class MrcpManage {
    private ConcurrentHashMap<String, MrcpCallData> mrcpCallDataConcurrentHashMap = new ConcurrentHashMap<>();

    public void updateConnection(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            MrcpCallData mrcpCallData = new MrcpCallData();
            mrcpCallData.setCallId(callId);
            mrcpCallDataConcurrentHashMap.put(callId, mrcpCallData);
        }
    }

    /**
     * 添加asr的处理器
     * @param callId pbx的uuid
     * @param asrHandler asr的核心处理流程
     */
    public void addNewAsr(String callId, AsrHandler asrHandler) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            MrcpCallData mrcpCallData = new MrcpCallData();
            mrcpCallData.setCallId(callId);
            mrcpCallData.setAsrHandler(asrHandler);
            mrcpCallDataConcurrentHashMap.put(callId, mrcpCallData);
        } else {
            MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
            mrcpCallData.setAsrHandler(asrHandler);
        }
    }

    /**
     * 添加tts的处理器
     * @param callId pbx的uuid
     * @param ttsHandler tts的核心处理流程
     */
    public void addNewTts(String callId, TtsHandler ttsHandler) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            MrcpCallData mrcpCallData = new MrcpCallData();
            mrcpCallData.setCallId(callId);
            mrcpCallData.setTtsHandler(ttsHandler);
            mrcpCallDataConcurrentHashMap.put(callId, mrcpCallData);
        } else {
            MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
            mrcpCallData.setTtsHandler(ttsHandler);
        }
    }

    public AsrHandler getAsrHandler(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.warn("getAsrHandler error, callId:{} not exist", callId);
            return null;
        } else {
            return mrcpCallDataConcurrentHashMap.get(callId).getAsrHandler();
        }
    }

    public TtsHandler getTtsHandler(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.warn("getTtsHandler error, callId:{} not exist", callId);
            return null;
        } else {
            return mrcpCallDataConcurrentHashMap.get(callId).getTtsHandler();
        }
    }

    public void setSpeaking(String callId, boolean isSpeaking) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("setSpeaking error, callId:{} not exist", callId);
        }
        mrcpCallDataConcurrentHashMap.get(callId).setSpeaking(isSpeaking);
    }

    public void setRealTimeAudioProcessor(String callId, RealTimeAudioProcessor realTimeAudioProcessor) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("setRealTimeAudioProcessor error, callId:{} not exist", callId);
        }
        mrcpCallDataConcurrentHashMap.get(callId).setRealTimeAudioProcessor(realTimeAudioProcessor);
    }

    public void interrupt(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("interrupt error, callId:{} not exist", callId);
            return;
        }
        MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
        if (mrcpCallData.isSpeaking()) {
            // 1. 停止tts
            mrcpCallDataConcurrentHashMap.get(callId).getTtsHandler().ttsClose();
            // 2. 停止rtp数据发送
            mrcpCallDataConcurrentHashMap.get(callId).getTtsHandler().getProcessor().interrupt();
            setSpeaking(callId, false);
        }
    }

    public void removeMrcpCallData(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.warn("removeMrcpCallData error, callId:{} not exist", callId);
            return;
        }
        mrcpCallDataConcurrentHashMap.remove(callId);
    }
}
