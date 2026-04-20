package com.cfsl.easymrcp.tts.scheduler;

import com.cfsl.easymrcp.tts.NettyTtsRtpProcessor4;

/**
 * 处理任务。
 *
 * <p>它只是一个轻量包装，表达“某个 taskId 对应哪个 Processor4 实例”。
 * 真正的处理执行仍然由所属 {@link ProcessWorker} 在线程循环里驱动。
 */
final class ProcessTask {
    private final String taskId;
    private final NettyTtsRtpProcessor4 processor;

    ProcessTask(String taskId, NettyTtsRtpProcessor4 processor) {
        this.taskId = taskId;
        this.processor = processor;
    }

    String getTaskId() {
        return taskId;
    }

    NettyTtsRtpProcessor4 getProcessor() {
        return processor;
    }
}
