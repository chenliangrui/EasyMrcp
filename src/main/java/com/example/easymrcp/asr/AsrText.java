package com.example.easymrcp.asr;

import com.example.easymrcp.mrcp.AsrCallback;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

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
                xfyunAsrCallback.apply(result);
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
