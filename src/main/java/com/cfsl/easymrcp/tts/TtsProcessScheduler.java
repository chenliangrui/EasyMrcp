package com.cfsl.easymrcp.tts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * TTS 处理调度器。
 * 结构与 TtsRtpScheduler 保持一致，只负责共享线程轮询调用 processOnce()，不承载具体业务逻辑。
 */
@Slf4j
@Component
public class TtsProcessScheduler {

    @Value("${tts.process-scheduler.pool-size:1}")
    private int schedulerPoolSize;

    @Value("${tts.process-scheduler.interval-ms:20}")
    private int processIntervalMs;

    private final List<List<ProcessTask>> taskGroups = new ArrayList<>();
    private final List<Thread> schedulerThreads = new ArrayList<>();
    private final ConcurrentHashMap<String, ProcessTask> taskMap = new ConcurrentHashMap<>();
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private volatile boolean running = true;

    /**
     * 初始化共享处理线程组。
     */
    @PostConstruct
    public void init() {
        for (int i = 0; i < schedulerPoolSize; i++) {
            taskGroups.add(Collections.synchronizedList(new ArrayList<>()));
        }
        for (int i = 0; i < schedulerPoolSize; i++) {
            final int groupIndex = i;
            Thread thread = new Thread(() -> runSchedulerLoop(groupIndex), "tts-process-scheduler-" + (groupIndex + 1));
            thread.setDaemon(true);
            schedulerThreads.add(thread);
            thread.start();
        }
        log.info("TTS 处理调度器初始化完成，线程数: {}", schedulerPoolSize);
    }

    /**
     * 注册一个处理任务，由共享线程周期性驱动对应 processor 的 processOnce()。
     */
    public String register(NettyTtsRtpProcessor4 processor) {
        String taskId = UUID.randomUUID().toString();
        ProcessTask task = new ProcessTask(taskId, processor);
        int groupIndex = taskCount.getAndIncrement() % schedulerPoolSize;
        taskGroups.get(groupIndex).add(task);
        taskMap.put(taskId, task);
        log.debug("TTS 处理任务已注册，任务ID: {}, 分组索引: {}", taskId, groupIndex);
        return taskId;
    }

    /**
     * 取消指定处理任务。
     */
    public void cancel(String taskId) {
        ProcessTask task = taskMap.remove(taskId);
        if (task != null) {
            for (List<ProcessTask> group : taskGroups) {
                group.remove(task);
            }
            log.debug("TTS 处理任务已取消，任务ID: {}", taskId);
        }
    }

    /**
     * 每个线程轮询自己负责的任务分组，并按固定周期调用 processOnce()。
     */
    private void runSchedulerLoop(int groupIndex) {
        List<ProcessTask> taskGroup = taskGroups.get(groupIndex);
        final long intervalNanos = processIntervalMs * 1_000_000L;
        long shouldWakeUp = System.nanoTime();

        while (running && !Thread.currentThread().isInterrupted()) {
            long startTime = System.nanoTime();
            long timeDifference = startTime - shouldWakeUp;
            for (ProcessTask task : taskGroup) {
                try {
                    task.processor.processOnce();
                } catch (Exception e) {
                    log.error("处理任务执行异常，任务ID: {}", task.taskId, e);
                }
            }
            long endTime = System.nanoTime();
            long sleepNanos = intervalNanos - (endTime - startTime) - timeDifference;
            shouldWakeUp = endTime + sleepNanos;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
        log.debug("TTS 处理调度线程已结束，分组索引: {}", groupIndex);
    }

    /**
     * 应用关闭时统一停止所有处理线程。
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        for (Thread thread : schedulerThreads) {
            thread.interrupt();
        }
    }

    private static class ProcessTask {
        private final String taskId;
        private final NettyTtsRtpProcessor4 processor;

        private ProcessTask(String taskId, NettyTtsRtpProcessor4 processor) {
            this.taskId = taskId;
            this.processor = processor;
        }
    }
}
