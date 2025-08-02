package com.cfsl.easymrcp.mrcp;

import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.util.Timeout;
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
    // 超时相关字段
    private Long noInputTimeout;           // 无输入超时
    // 实际应用中不太有用，已注释
    private Long recognitionTimeout;       // 识别总超时
    private Boolean startInputTimers;      // 是否启动输入计时器

    // 当前活动的超时任务
    private Timeout noInputTimeoutTask;

    // 超时回调
    private TimeoutCallback timeoutCallback;

    public interface TimeoutCallback {
        void onNoInputTimeout();

        // Speech-Complete-Timeout由VAD直接处理，但保留接口方法供VAD检测到语音结束时调用
        void onSpeechCompleteTimeout();
    }

    public MrcpTimeoutManager(TimeoutCallback callback) {
        this.timeoutCallback = callback;

        // 默认超时值（毫秒）
        this.noInputTimeout = 500000L;
        this.recognitionTimeout = 100000L;
        this.startInputTimers = true;
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
        log.debug("Starting timers: no-input={}", noInputTimeout);
        // 启动无输入超时定时器
        if (noInputTimeout > 0) {
            if (noInputTimeoutTask != null) {
                cancelAllTimers();
            }
            noInputTimeoutTask = SipUtils.wheelTimer.newTimeout(timeout -> {
                SipUtils.executeTask(() -> {
                    log.info("No input timeout triggered");
                    timeoutCallback.onNoInputTimeout();
                });
            }, noInputTimeout, TimeUnit.MILLISECONDS);
        }
    }

    // 取消所有定时器
    public void cancelAllTimers() {
        if (noInputTimeoutTask != null) {
            noInputTimeoutTask.cancel();
            noInputTimeoutTask = null;
            log.debug("Cancelling all timers");
        }
    }

    // 启动输入计时器的方法(响应START_INPUT_TIMERS请求)
    public void startInputTimers() {
        log.debug("Explicitly starting input timers");
        startTimers();
    }
} 