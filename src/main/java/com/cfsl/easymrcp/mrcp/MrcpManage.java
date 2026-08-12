package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.tcp.MrcpEventWithCallback;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 业务层面全局管理mrcp通话
 */
@Slf4j
@Component
public class MrcpManage {
    // MRCP事件处理线程池配置
    @Value("${mrcp.event-thread-pool.core-pool-size:8}")
    private int eventCorePoolSize;
    @Value("${mrcp.event-thread-pool.max-pool-size:100}")
    private int eventMaxPoolSize;
    @Value("${mrcp.event-thread-pool.keep-alive-seconds:60}")
    private long eventKeepAliveSeconds;
    @Value("${mrcp.event-thread-pool.queue-capacity:1000}")
    private int eventQueueCapacity;

    ThreadPoolExecutor mrcpEventThreadPool;

    private ConcurrentHashMap<String, MrcpCallData> mrcpCallDataConcurrentHashMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化MRCP事件处理线程池
        mrcpEventThreadPool = new ThreadPoolExecutor(
                eventCorePoolSize,
                eventMaxPoolSize,
                eventKeepAliveSeconds, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(eventQueueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("MRCP事件处理线程池初始化完成: core={}, max={}, queueCapacity={}",
                eventCorePoolSize, eventMaxPoolSize, eventQueueCapacity);
    }

    public void updateConnection(String callId, CountDownLatch countDownLatch, String asrEngineName) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            MrcpCallData mrcpCallData = new MrcpCallData();
            mrcpCallData.setCallId(callId);
            mrcpCallData.setSipLatch(countDownLatch);
            if (asrEngineName != null) {
                mrcpCallData.setAsrEngineName(asrEngineName);
            }
            mrcpCallDataConcurrentHashMap.put(callId, mrcpCallData);
        } else {
            MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
            if (asrEngineName != null) {
                mrcpCallData.setAsrEngineName(asrEngineName);
            }
            // sip等待client连接情况，ASR引擎参数写入完成后放行等待
            if (mrcpCallData.getSipLatch() != null) {
                mrcpCallData.getSipLatch().countDown();
            }
        }
    }

    public boolean containsCallId(String callId) {
        return mrcpCallDataConcurrentHashMap.containsKey(callId);
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

    public String getAsrEngineName(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("getAsrEngineName error, callId:{} not exist", callId);
            return null;
        }
        return mrcpCallDataConcurrentHashMap.get(callId).getAsrEngineName();
    }

    public TtsHandler getTtsHandler(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.warn("getTtsHandler error, callId:{} not exist", callId);
            return null;
        } else {
            return mrcpCallDataConcurrentHashMap.get(callId).getTtsHandler();
        }
    }

    public void setTtsEngineName(String callId, String ttsEngine) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("setTtsEngineName error, callId:{} not exist", callId);
        }
        mrcpCallDataConcurrentHashMap.get(callId).setTtsEngineName(ttsEngine);
    }

    public void setVoice(String callId, String voice) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("setVoice error, callId:{} not exist", callId);
        }
        mrcpCallDataConcurrentHashMap.get(callId).setVoice(voice);
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

    public Boolean getPushAsrRealtimeResult(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("getPushAsrRealtimeResult error, callId:{} not exist", callId);
        }
        return mrcpCallDataConcurrentHashMap.get(callId).getPushAsrRealtimeResult();
    }

    public void setPushAsrRealtimeResult(String callId, Boolean pushAsrRealtimeResult) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("setPushAsrRealtimeResult error, callId:{} not exist", callId);
        }
        mrcpCallDataConcurrentHashMap.get(callId).setPushAsrRealtimeResult(pushAsrRealtimeResult);
    }

    public String getTtsEngineName(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("getTtsEngine error, callId:{} not exist", callId);
        }
        return mrcpCallDataConcurrentHashMap.get(callId).getTtsEngineName();
    }

    public String getVoice(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("getVoice error, callId:{} not exist", callId);
        }
        return mrcpCallDataConcurrentHashMap.get(callId).getVoice();
    }

    public void interrupt(String callId) {
        if (!mrcpCallDataConcurrentHashMap.containsKey(callId)) {
            log.error("interrupt error, callId:{} not exist", callId);
            return;
        }
        MrcpCallData mrcpCallData = mrcpCallDataConcurrentHashMap.get(callId);
        if (mrcpCallData.isSpeaking()) {
            // 1. 停止tts
            mrcpCallDataConcurrentHashMap.get(callId).getTtsHandler().getTtsProcessor().ttsClose();
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
        if (mrcpCallData.getAsrHandler() != null) {
            mrcpCallData.getAsrHandler().close();
        }
        if (mrcpCallData.getTtsHandler() != null) {
            mrcpCallData.getTtsHandler().close();
        }
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
                mrcpEventQueue.take().getConsumer().accept("normal");
            } else {
                if (event.getEventType().equals(TcpEventType.Silence.name())) {
                    return;
                }
                // 判断是否预加载
                MrcpEventWithCallback peek = mrcpEventQueue.peek();
                if (peek == event) {
                    event.setPre(true);
                    log.debug("开始预加载");
                    // 预加载
                    mrcpEventThreadPool.execute(() -> {
                        peek.getConsumer().accept("pre");
                    });
                }
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
//                mrcpEventThreadPool.execute(take.getConsumer());
                mrcpEventThreadPool.execute(() -> {
                    if (take.isPre()) {
                        take.getConsumer().accept("playPre");
                    } else {
                        take.getConsumer().accept("normal");
                    }

                    // 判断是否需要预加载下一个Speak
                    MrcpEventWithCallback peek = mrcpEventQueue.peek();
                    if (peek != null && !peek.getEventType().equals(TcpEventType.Silence.name())) {
                        // 预加载下一个Speak
                        peek.setPre(true);
                        log.info("预加载下一个Speak");
                        // 预加载
                        mrcpEventThreadPool.execute(() -> {
                            peek.getConsumer().accept("pre");
                        });
                    }
                });
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
