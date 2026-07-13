package com.cfsl.easymrcp.tts.scheduler;

/**
 * RTP 发送任务抽象。
 *
 * <p>该抽象定义了：
 * <ul>
 *     <li>任务唯一标识</li>
 *     <li>取消标记</li>
 *     <li>单轮发送入口</li>
 *     <li>资源释放入口</li>
 * </ul>
 *
 * <p>具体的发送语义由 {@link RtpSendTask} 实现，worker 只负责调度其执行时机。
 */
abstract class AbstractRtpSendTask {
    private final String taskId;
    private volatile boolean canceled;

    AbstractRtpSendTask(String taskId) {
        this.taskId = taskId;
    }

    String getTaskId() {
        return taskId;
    }

    void markCanceled() {
        this.canceled = true;
    }

    boolean isCanceled() {
        return canceled;
    }

    /** 执行一次发送推进，由外层 worker 按节拍调用。 */
    abstract void doSendOnce(long nowNanos);

    /** 释放任务私有资源，默认无操作。 */
    void release() {
    }
}
