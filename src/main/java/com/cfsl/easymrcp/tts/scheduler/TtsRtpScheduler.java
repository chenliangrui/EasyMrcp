package com.cfsl.easymrcp.tts.scheduler;

import com.cfsl.easymrcp.rtp.NettyAudioRingBuffer;
import com.cfsl.easymrcp.rtp.NettyRtpSenderV4;
import com.cfsl.easymrcp.tts.NettyTtsRtpProcessor4;
import com.cfsl.easymrcp.tts.TTSConstant;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * TTS RTP 发送调度器（按需伸缩 worker 模式）。
 * 保留共享发送线程框架，同时让 output 的消费语义参考 V3 单帧发送逻辑。
 */
/**
 * TTS RTP 发送调度器。
 *
 * <p>该类负责全局 worker 路由、扩缩容和任务归属。
 * 单个 worker 的任务集合、取消、延迟释放和发送节拍由 worker / task 自己管理，
 * 调度器不在热路径里直接改 worker 内部任务集合。
 */
@Slf4j
@Component
public class TtsRtpScheduler {

    @Value("${tts.rtp-scheduler.send-interval-ms:20}")
    private int sendIntervalMs;

    @Value("${tts.rtp-scheduler.worker-capacity:50}")
    private int workerCapacity;

    @Value("${tts.rtp-scheduler.expand-threshold:0.8}")
    private double expandThreshold;

    @Value("${tts.rtp-scheduler.idle-timeout-ms:60000}")
    private long idleTimeoutMs;

    @Value("${tts.rtp-scheduler.min-workers:0}")
    private int minWorkers;

    @Value("${tts.rtp-scheduler.max-workers:20}")
    private int maxWorkers;

    private final Map<Integer, RtpWorker> workers = new HashMap<>();
    private final Map<String, Integer> taskOwnerIndexMap = new HashMap<>();
    private final Object lifecycleLock = new Object();
    private volatile boolean running = true;
    private int maxWorkerIndex = -1;

    @PostConstruct
    public void init() {
        log.info("TTS RTP 发送调度器初始化完成，按需伸缩模式已启用");
    }

    /**
     * 注册一个发送任务。
     *
     * <p>调度器只负责找到目标 worker 并把任务投递进去，
     * 之后由 worker 自己线程把任务纳入 active 集合并驱动发送。
     */
    public String register(NettyTtsRtpProcessor4 processor, Consumer<String> callback) {
        synchronized (lifecycleLock) {
            if (!running) {
                throw new IllegalStateException("TTS RTP 调度器已关闭");
            }
            RtpWorker worker = findOrCreateAvailableWorkerLocked();
            String taskId = UUID.randomUUID().toString();
            AbstractRtpSendTask task = new RtpSendTask(taskId, processor, callback);
            worker.enqueueAdd(task);
            taskOwnerIndexMap.put(taskId, worker.getIndex());
            worker.startIfNecessary(() -> runWorkerLoop(worker), "tts-rtp-scheduler-" + (worker.getIndex() + 1));
            log.debug("RTP发送任务已注册，任务ID: {}, workerIndex: {}", taskId, worker.getIndex());
            return taskId;
        }
    }

    /**
     * 取消一个发送任务。
     *
     * <p>这里不直接释放任务资源，而是把取消请求投递给 worker，
     * 由 worker 在自己的线程上下文里完成取消标记和延迟释放。
     */
    public void cancel(String taskId) {
        RtpWorker worker;
        Integer ownerIndex;
        synchronized (lifecycleLock) {
            ownerIndex = taskOwnerIndexMap.remove(taskId);
            if (ownerIndex == null) {
                return;
            }
            worker = workers.get(ownerIndex);
        }
        if (worker == null) {
            return;
        }
        worker.enqueueCancel(taskId);
        log.debug("RTP发送任务已取消，任务ID: {}, workerIndex: {}", taskId, ownerIndex);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        List<Thread> threads = new ArrayList<>();
        List<AbstractRtpSendTask> tasksToRelease = new ArrayList<>();
        synchronized (lifecycleLock) {
            for (RtpWorker worker : workers.values()) {
                worker.stop();
                worker.drainOwnedTasks(tasksToRelease);
                if (worker.getThread() != null) {
                    threads.add(worker.getThread());
                }
            }
            workers.clear();
            taskOwnerIndexMap.clear();
            maxWorkerIndex = -1;
        }
        for (Thread thread : threads) {
            thread.interrupt();
        }
        for (Thread thread : threads) {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待发送调度线程结束被中断", e);
            }
        }
        for (AbstractRtpSendTask task : tasksToRelease) {
            task.release();
        }
        log.info("TTS RTP 调度器已关闭");
    }

    /**
     * 在当前 worker 集合中寻找可承载新发送任务的 worker；
     * 如果全部达到阈值，则新建一个更高 index 的 worker。
     */
    private RtpWorker findOrCreateAvailableWorkerLocked() {
        for (int index = 0; index <= maxWorkerIndex; index++) {
            RtpWorker worker = workers.get(index);
            if (worker != null && worker.taskCount() < expandLimit(worker)) {
                return worker;
            }
        }
        if (workers.size() >= maxWorkers) {
            throw new IllegalStateException("TTS RTP 调度器 worker 数已达到上限: " + maxWorkers);
        }
        int newIndex = maxWorkerIndex + 1;
        RtpWorker worker = new RtpWorker(newIndex, workerCapacity);
        workers.put(newIndex, worker);
        maxWorkerIndex = newIndex;
        return worker;
    }

    /**
     * 扩容阈值按“任务数 / 容量”计算，语义类似 HashMap 装载因子。
     */
    private int expandLimit(RtpWorker worker) {
        return Math.max(1, (int) Math.ceil(worker.getCapacity() * expandThreshold));
    }

    /**
     * worker 主循环。
     *
     * <p>worker 先合并自己的待新增/待取消请求，再直接遍历自己拥有的活动任务集合。
     * 这里不做全局任务快照，发送热路径只围绕当前 worker 自己的数据结构展开。
     */
    private void runWorkerLoop(RtpWorker worker) {
        final long schedulerIntervalNanos = sendIntervalMs * 1_000_000L;
        long shouldWakeUp = System.nanoTime();
        while (running && worker.isRunning() && !Thread.currentThread().isInterrupted()) {
            long startTime = System.nanoTime();
            long timeDifference = startTime - shouldWakeUp;

            worker.applyPendingChanges();
            if (worker.hasActiveTasks()) {
                worker.clearIdle();
                sendAllTasks(worker.activeTasksView(), startTime);
            } else {
                worker.markIdleIfNecessary();
            }
            worker.releasePendingTasks();
            if (!worker.hasActiveTasks() && tryShrinkTailWorker(worker)) {
                break;
            }

            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            long sleepNanos = schedulerIntervalNanos - elapsed - timeDifference;
            shouldWakeUp = endTime + sleepNanos;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
        worker.releasePendingTasks();
        synchronized (lifecycleLock) {
            worker.onLoopExit(Thread.currentThread());
        }
        log.debug("发送调度线程已结束，workerIndex: {}", worker.getIndex());
    }

    /**
     * 执行当前 worker 的所有活动发送任务。
     *
     * <p>取消中的任务会先被 worker 标记，这里直接跳过即可。
     */
    private void sendAllTasks(Iterable<AbstractRtpSendTask> tasks, long nowNanos) {
        for (AbstractRtpSendTask task : tasks) {
            if (task.isCanceled()) {
                continue;
            }
            try {
                task.doSendOnce(nowNanos);
            } catch (Exception e) {
                log.error("RTP发送任务执行异常，任务ID: {}", task.getTaskId(), e);
            }
        }
    }

    /**
     * 只允许尾部 worker 缩容。
     *
     * <p>这样可以保持 worker index 语义稳定，并避免 rebalance。
     */
    private boolean tryShrinkTailWorker(RtpWorker worker) {
        synchronized (lifecycleLock) {
            if (worker.taskCount() > 0 || worker.hasActiveTasks()) {
                worker.clearIdle();
                return false;
            }
            if (workers.size() <= minWorkers) {
                return false;
            }
            if (worker.getIndex() != maxWorkerIndex) {
                return false;
            }
            if (worker.getIdleStartTime() == 0L) {
                worker.setIdleStartTime(System.currentTimeMillis());
                return false;
            }
            if (System.currentTimeMillis() - worker.getIdleStartTime() < idleTimeoutMs) {
                return false;
            }
            workers.remove(worker.getIndex());
            while (maxWorkerIndex >= 0 && !workers.containsKey(maxWorkerIndex)) {
                maxWorkerIndex--;
            }
            worker.stop();
            return true;
        }
    }
}
