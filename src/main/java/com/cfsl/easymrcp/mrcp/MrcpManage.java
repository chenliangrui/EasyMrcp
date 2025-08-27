package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.tcp.MrcpEventWithCallback;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 业务层面全局管理mrcp通话
 */
@Slf4j
@Component
public class MrcpManage {
    ThreadPoolExecutor mrcpEventThreadPool = new ThreadPoolExecutor(
            8,                      // 核心线程数
            100,                     // 最大线程数
            60L, TimeUnit.SECONDS,  // 空闲线程最大存活时间
            new LinkedBlockingQueue<>(), // 队列，用来存放任务
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：任务由提交线程执行
    );

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
     *
     * @param callId     pbx的uuid
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
     *
     * @param callId     pbx的uuid
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

    public boolean isInterruptEnable(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("isInterruptEnable error, callId:{} not exist", callId);
        }
        return mrcpCallDataConcurrentHashMap.get(callId).isInterruptEnable();
    }

    public AtomicBoolean getInterruptEnable(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("getInterruptEnable error, callId:{} not exist", callId);
        }
        return mrcpCallDataConcurrentHashMap.get(callId).getInterruptEnable();
    }

    public void setInterruptEnable(String callId, boolean interruptEnable) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("setInterruptEnable error, callId:{} not exist", callId);
        }
        mrcpCallDataConcurrentHashMap.get(callId).setInterruptEnable(interruptEnable);
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
            mrcpCallDataConcurrentHashMap.get(callId).getTtsHandler().interrupt();
            setSpeaking(callId, false);
        }
    }

    // 暂时在mrcp会话关闭时不做处理
    public void removeMrcpCallData(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.warn("removeMrcpCallData error, callId:{} not exist", callId);
            return;
        }
        mrcpCallDataConcurrentHashMap.remove(callId);
    }

    public void close(String uuid) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(uuid)) {
            log.warn("removeMrcpCallData error, callId:{} not exist", uuid);
            return;
        }
        MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(uuid);
        mrcpCallData.getAsrHandler().close();
        mrcpCallData.getTtsHandler().close();
        mrcpCallDataConcurrentHashMap.remove(uuid);
    }


    /**
     * 添加speak事件
     * @param callId 通话的uuid
     * @param event 队列中取出任务的执行回调
     */
    public void addEvent(String callId, MrcpEventWithCallback event) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.warn("addEvent error, callId:{} not exist", callId);
            return;
        }
        MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
        try {
            LinkedBlockingQueue<MrcpEventWithCallback> mrcpEventQueue = mrcpCallData.getMrcpEventQueue();
            mrcpEventQueue.put(event);
            if (!mrcpCallData.isSpeaking()) {
                mrcpEventQueue.take().getRunnable().run();
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 当完成tts时，执行下一个speak
     * @param callId 通话的uuid
     */
    public void runNextSpeak(String callId) {
        try {
            MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
            LinkedBlockingQueue<MrcpEventWithCallback> mrcpEventQueue = mrcpCallData.getMrcpEventQueue();
            if (!mrcpEventQueue.isEmpty()) {
                MrcpEventWithCallback take = mrcpEventQueue.take();
                mrcpEventThreadPool.execute(take.getRunnable());
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 当有asr打断时，清除所有等待speak的任务
     * @param callId 通话的uuid
     */
    public void clearAllSpeakTask(String callId) {
        MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
        LinkedBlockingQueue<MrcpEventWithCallback> mrcpEventQueue = mrcpCallData.getMrcpEventQueue();
        mrcpEventQueue.clear();
    }

    public void clearAllSpeakTaskAndInterrupt(String callId) {
        // 清除speak队列中未完成的任务
        clearAllSpeakTask(callId);
        // 中断当前TTS
        interrupt(callId);
    }

    public void executeTask(Runnable runnable) {
        mrcpEventThreadPool.execute(runnable);
    }
}
