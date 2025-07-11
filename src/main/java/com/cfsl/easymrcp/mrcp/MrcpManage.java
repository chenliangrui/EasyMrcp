package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.tts.RealTimeAudioProcessor;
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

    public void setSpeaking(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            MrcpCallData mrcpCallData = new MrcpCallData();
            mrcpCallData.setCallId(callId);
            mrcpCallDataConcurrentHashMap.put(callId, mrcpCallData);
        }
        mrcpCallDataConcurrentHashMap.get(callId).setSpeaking(true);
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
        mrcpCallDataConcurrentHashMap.get(callId).getRealTimeAudioProcessor().interrupt();
    }
}
