package com.cfsl.easymrcp.mrcp;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * MRCP超时管理器
 * 用于处理MRCP协议中定义的各种超时参数:
 * - Speech-Complete-Timeout: 检测到语音后，静音持续多长时间被视为说话结束 (已由VAD直接处理，此处不再计时)
 * // 实际应用中不太有用，已注释
 * // - Speech-Incomplete-Timeout: 检测到部分语音后，在一定时间内没有检测到后续语音
 * - No-Input-Timeout: 识别开始后，多长时间内未检测到任何语音输入则超时
 * // 实际应用中不太有用，已注释
 * // - Recognition-Timeout: 整个识别会话的最大持续时间
 * - Start-Input-Timers: 是否立即启动输入相关的计时器
 */
@Slf4j
public class MrcpTimeoutManager {
    // 单例模式
    private static final HashedWheelTimer wheelTimer = new HashedWheelTimer(50, TimeUnit.MILLISECONDS);
    
    // 超时相关字段
    private Long speechCompleteTimeout;    // 语音完成超时 (已由VAD直接处理)
    // 实际应用中不太有用，已注释
    private Long speechIncompleteTimeout;  // 语音不完整超时
    private Long noInputTimeout;           // 无输入超时
    // 实际应用中不太有用，已注释
    private Long recognitionTimeout;       // 识别总超时
    private Boolean startInputTimers;      // 是否启动输入计时器
    
    // 当前活动的超时任务
    private Timeout noInputTimeoutTask;
    // 不再单独计时，依赖VAD判断语音结束
    // private Timeout speechCompleteTimeoutTask;
    // 实际应用中不太有用，已注释
    private Timeout speechIncompleteTimeoutTask;
    // 实际应用中不太有用，已注释
    private Timeout recognitionTimeoutTask;
    
    // 超时回调
    private TimeoutCallback timeoutCallback;
    
    // 当前状态
    private boolean speechDetected = false;
    
    public interface TimeoutCallback {
        void onNoInputTimeout();
        // Speech-Complete-Timeout由VAD直接处理，但保留接口方法供VAD检测到语音结束时调用
        void onSpeechCompleteTimeout();
        // 实际应用中不太有用，已注释
        void onSpeechIncompleteTimeout();
        // 实际应用中不太有用，已注释
        void onRecognitionTimeout();
    }
    
    public MrcpTimeoutManager(TimeoutCallback callback) {
        this.timeoutCallback = callback;
        
        // 默认超时值（毫秒）
        this.speechCompleteTimeout = 1500L;
        this.speechIncompleteTimeout = 3000L;
        this.noInputTimeout = 500000L;
        this.recognitionTimeout = 100000L;
        this.startInputTimers = true;
    }
    
    // 保留设置方法，但Speech-Complete-Timeout已由VAD直接处理
    public void setSpeechCompleteTimeout(Long timeout) {
        if (timeout != null && timeout > 0) {
            this.speechCompleteTimeout = timeout;
        }
    }
    
    public void setSpeechIncompleteTimeout(Long timeout) {
        if (timeout != null && timeout > 0) {
            this.speechIncompleteTimeout = timeout;
        }
    }
    
    public void setNoInputTimeout(Long timeout) {
        if (timeout != null && timeout > 0) {
            this.noInputTimeout = timeout;
        }
    }
    
    public void setRecognitionTimeout(Long timeout) {
        if (timeout != null && timeout > 0) {
            this.recognitionTimeout = timeout;
        }
    }
    
    public void setStartInputTimers(Boolean start) {
        if (start != null) {
            this.startInputTimers = start;
        }
    }
    
    public void startTimers() {
        // 如果不立即开始计时，则直接返回
        if (!startInputTimers) {
            log.info("Start-Input-Timers is false, waiting for explicit start");
            return;
        }
        
        log.info("Starting timers: no-input={}", noInputTimeout);
        
        // 实际应用中不太有用，已注释
        /*
        // 启动识别总超时定时器
        if (recognitionTimeout > 0) {
            recognitionTimeoutTask = wheelTimer.newTimeout(timeout -> {
                log.info("Recognition timeout triggered");
                timeoutCallback.onRecognitionTimeout();
            }, recognitionTimeout, TimeUnit.MILLISECONDS);
        }
        */
        
        // 启动无输入超时定时器
        if (noInputTimeout > 0) {
            noInputTimeoutTask = wheelTimer.newTimeout(timeout -> {
                if (!speechDetected) {
                    log.info("No input timeout triggered");
                    timeoutCallback.onNoInputTimeout();
                }
            }, noInputTimeout, TimeUnit.MILLISECONDS);
        }
    }
    
    // 当检测到语音时调用
    public void onSpeechStart() {
        speechDetected = true;
        log.debug("Speech detected, canceling no-input timeout");
        
        // 取消无输入超时
        if (noInputTimeoutTask != null && !noInputTimeoutTask.isExpired()) {
            noInputTimeoutTask.cancel();
        }
        
        // 实际应用中不太有用，已注释
        /*
        // 启动语音不完整超时
        if (speechIncompleteTimeout > 0) {
            log.debug("Starting speech-incomplete timeout: {}", speechIncompleteTimeout);
            speechIncompleteTimeoutTask = wheelTimer.newTimeout(timeout -> {
                log.info("Speech incomplete timeout triggered");
                timeoutCallback.onSpeechIncompleteTimeout();
            }, speechIncompleteTimeout, TimeUnit.MILLISECONDS);
        }
        */
    }
    
//    public void onSpeechContinue() {
//        // 实际应用中不太有用，已注释
//        /*
//        // 语音正在进行，重置不完整超时
//        if (speechIncompleteTimeoutTask != null && !speechIncompleteTimeoutTask.isExpired()) {
//            speechIncompleteTimeoutTask.cancel();
//        }
//        
//        if (speechIncompleteTimeout > 0) {
//            log.debug("Refreshing speech-incomplete timeout: {}", speechIncompleteTimeout);
//            speechIncompleteTimeoutTask = wheelTimer.newTimeout(timeout -> {
//                log.info("Speech incomplete timeout triggered");
//                timeoutCallback.onSpeechIncompleteTimeout();
//            }, speechIncompleteTimeout, TimeUnit.MILLISECONDS);
//        }
//        */
//    }
    
    // 当检测到语音结束时调用
    public void onSpeechEnd() {
        log.debug("Speech ended");
        // 实际应用中不太有用，已注释
        /*
        // 取消语音不完整超时
        if (speechIncompleteTimeoutTask != null && !speechIncompleteTimeoutTask.isExpired()) {
            speechIncompleteTimeoutTask.cancel();
        }
        */
        
        // 语音结束时直接调用回调，不再启动计时器
        log.info("Speech ended, notifying timeout callback");
        timeoutCallback.onSpeechCompleteTimeout();
        
        // 不再使用计时器
        /*
        // 启动语音完成超时
        if (speechCompleteTimeout > 0) {
            log.debug("Starting speech-complete timeout: {}", speechCompleteTimeout);
            speechCompleteTimeoutTask = wheelTimer.newTimeout(timeout -> {
                log.info("Speech complete timeout triggered");
                timeoutCallback.onSpeechCompleteTimeout();
            }, speechCompleteTimeout, TimeUnit.MILLISECONDS);
        }
        */
    }
    
    // 取消所有定时器
    public void cancelAllTimers() {
        log.debug("Cancelling all timers");
        if (noInputTimeoutTask != null) noInputTimeoutTask.cancel();
        // if (speechCompleteTimeoutTask != null) speechCompleteTimeoutTask.cancel();
        if (speechIncompleteTimeoutTask != null) speechIncompleteTimeoutTask.cancel();
        if (recognitionTimeoutTask != null) recognitionTimeoutTask.cancel();
    }
    
    // 启动输入计时器的方法(响应START_INPUT_TIMERS请求)
    public void startInputTimers() {
        log.info("Explicitly starting input timers");
        startTimers();
    }
} 