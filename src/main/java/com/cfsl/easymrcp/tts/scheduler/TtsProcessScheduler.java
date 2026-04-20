package com.cfsl.easymrcp.tts.scheduler;

import com.cfsl.easymrcp.tts.NettyTtsRtpProcessor4;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

/**
 * TTS 处理调度器。
 *
 * <p>该类只负责两件事：
 * <ul>
 *     <li>维护 worker 路由和扩缩容</li>
 *     <li>驱动 worker 线程周期性调用 {@link NettyTtsRtpProcessor4#processOnce()}</li>
 * </ul>
 *
 * <p>具体任务集合由 {@link ProcessWorker} 自己维护。
 * 调度器本身只掌握全局 worker 拓扑、任务归属和尾部缩容判断，不介入单个 worker 的热路径执行。
 */
@Slf4j
@Component
public class TtsProcessScheduler {

    @Value("${tts.process-scheduler.interval-ms:20}")
    private int processIntervalMs;

    @Value("${tts.process-scheduler.worker-capacity:50}")
    private int workerCapacity;

    @Value("${tts.process-scheduler.expand-threshold:0.8}")
    private double expandThreshold;

    @Value("${tts.process-scheduler.idle-timeout-ms:60000}")
    private long idleTimeoutMs;

    @Value("${tts.process-scheduler.min-workers:0}")
    private int minWorkers;

    @Value("${tts.process-scheduler.max-workers:20}")
    private int maxWorkers;

    /** 全局 worker 路由表，key 为 worker index。 */
    private final Map<Integer, ProcessWorker> workers = new HashMap<>();
    /** taskId -> workerIndex，用于 cancel 时快速定位所属 worker。 */
    private final Map<String, Integer> taskOwnerIndexMap = new HashMap<>();
    /** 仅保护调度器全局状态，不参与 worker 热路径执行。 */
    private final Object lifecycleLock = new Object();
    private volatile boolean running = true;
    private int maxWorkerIndex = -1;

    @PostConstruct
    public void init() {
        log.info("TTS 处理调度器初始化完成，按需伸缩模式已启用");
    }

    /**
     * 注册一个处理任务。
     *
     * <p>调度器只负责选择合适的 worker 并把任务交给 worker，
     * 之后由 worker 自己线程管理任务变更和执行过程。
     */
    public String register(NettyTtsRtpProcessor4 processor) {
        synchronized (lifecycleLock) {
            if (!running) {
                throw new IllegalStateException("TTS 处理调度器已关闭");
            }
            ProcessWorker worker = findOrCreateAvailableWorkerLocked();
            String taskId = UUID.randomUUID().toString();
            worker.enqueueAdd(new ProcessTask(taskId, processor));
            taskOwnerIndexMap.put(taskId, worker.getIndex());
            worker.startIfNecessary(() -> runWorkerLoop(worker), "tts-process-scheduler-" + (worker.getIndex() + 1));
            log.debug("TTS 处理任务已注册，任务ID: {}, workerIndex: {}", taskId, worker.getIndex());
            return taskId;
        }
    }

    /**
     * 取消一个处理任务。
     *
     * <p>这里不直接改 worker 正在遍历的活动任务集合，
     * 只把取消请求投递给目标 worker，由 worker 自己在线程上下文里处理。
     */
    public void cancel(String taskId) {
        ProcessWorker worker;
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
        log.debug("TTS 处理任务已取消，任务ID: {}, workerIndex: {}", taskId, ownerIndex);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        List<Thread> threads = new ArrayList<>();
        synchronized (lifecycleLock) {
            for (ProcessWorker worker : workers.values()) {
                worker.stop();
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
                log.warn("等待处理调度线程结束被中断", e);
            }
        }
        log.info("TTS 处理调度器已关闭");
    }

    /**
     * 在当前 worker 集合中寻找可承载新任务的 worker；
     * 如果全部达到阈值，则新建一个更高 index 的 worker。
     */
    private ProcessWorker findOrCreateAvailableWorkerLocked() {
        for (int index = 0; index <= maxWorkerIndex; index++) {
            ProcessWorker worker = workers.get(index);
            if (worker != null && worker.taskCount() < expandLimit(worker)) {
                return worker;
            }
        }
        if (workers.size() >= maxWorkers) {
            throw new IllegalStateException("TTS 处理调度器 worker 数已达到上限: " + maxWorkers);
        }
        int newIndex = maxWorkerIndex + 1;
        ProcessWorker worker = new ProcessWorker(newIndex, workerCapacity);
        workers.put(newIndex, worker);
        maxWorkerIndex = newIndex;
        return worker;
    }

    /**
     * 扩容阈值按“任务数 / 容量”计算，语义类似 HashMap 装载因子。
     */
    private int expandLimit(ProcessWorker worker) {
        return Math.max(1, (int) Math.ceil(worker.getCapacity() * expandThreshold));
    }

    /**
     * worker 主循环。
     *
     * <p>这里不读取全局快照，而是先让 worker 合并自己的待新增/待取消队列，
     * 然后直接遍历 worker 自己拥有的活动任务集合。
     */
    private void runWorkerLoop(ProcessWorker worker) {
        final long intervalNanos = processIntervalMs * 1_000_000L;
        long shouldWakeUp = System.nanoTime();
        while (running && worker.isRunning() && !Thread.currentThread().isInterrupted()) {
            long startTime = System.nanoTime();
            long timeDifference = startTime - shouldWakeUp;

            worker.applyPendingChanges();
            if (worker.hasActiveTasks()) {
                worker.clearIdle();
                for (ProcessTask task : worker.activeTasksView()) {
                    try {
                        task.getProcessor().processOnce();
                    } catch (Exception e) {
                        log.error("处理任务执行异常，任务ID: {}", task.getTaskId(), e);
                    }
                }
            } else {
                worker.markIdleIfNecessary();
                if (tryShrinkTailWorker(worker)) {
                    break;
                }
            }

            long endTime = System.nanoTime();
            long sleepNanos = intervalNanos - (endTime - startTime) - timeDifference;
            shouldWakeUp = endTime + sleepNanos;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
        synchronized (lifecycleLock) {
            worker.onLoopExit(Thread.currentThread());
        }
        log.debug("TTS 处理调度线程已结束，workerIndex: {}", worker.getIndex());
    }

    /**
     * 只允许尾部 worker 缩容。
     *
     * <p>这样可以保持 index 语义稳定，并避免 rebalance。
     */
    private boolean tryShrinkTailWorker(ProcessWorker worker) {
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
