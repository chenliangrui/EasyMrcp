package com.cfsl.easymrcp.asr;

import com.cfsl.easymrcp.mrcp.AsrCallback;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 实时语音转写文本判断
 * 某些实时语音转写没有明确返回一段话结束的标志，所以需要判断每次说完话之后n秒内是否有再次说话。
 * 如果没有再次说话，那么就认为一段话结束了，此时返回一段话的文本。如果有再次说话，那么就认为上
 * 一段话还没有结束，继续等待。
 */
public class AsrText {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private final ReentrantLock lock = new ReentrantLock();
    private AsrCallback xfyunAsrCallback;
    String result = "";

    public AsrText(AsrCallback xfyunAsrCallback) {
        this.xfyunAsrCallback = xfyunAsrCallback;
    }

    // 线程安全的文本更新
    public void updateAccumulatedText(String newText) {
        lock.lock();
        try {
            result = newText;
        } finally {
            lock.unlock();
        }
    }

    // 重置定时器
    public void resetTimer() {
        lock.lock();
        try {
            // 取消现有定时任务
            if (scheduledFuture != null && !scheduledFuture.isDone()) {
                scheduledFuture.cancel(false);
            }
            // 创建新定时任务
            scheduledFuture = executor.schedule(() -> {
                xfyunAsrCallback.apply(ASRConstant.Result, result);
                lock.lock();
                try {
                    result = "";
                } finally {
                    lock.unlock();
                }
            }, 2, TimeUnit.SECONDS);
        } finally {
            lock.unlock();
        }
    }
}
