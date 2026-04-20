package com.cfsl.easymrcp.tts.scheduler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 处理 worker。
 *
 * <p>一个 worker 对应一个实际线程，独占管理自己的任务集合。
 * 外部线程不会直接修改 {@code activeTasks}，只会把新增/取消请求投递到队列里，
 * 再由 worker 自己在线程循环中合并这些变更。
 */
final class ProcessWorker {
    private final int index;
    private final int capacity;
    /** 当前任务总数，用于扩缩容判断，不要求和 activeTasks 严格同一时刻一致。 */
    private final AtomicInteger taskCount = new AtomicInteger();
    /** 当前已生效、由 worker 自己线程直接遍历执行的任务集合。 */
    private final Map<String, ProcessTask> activeTasks = new HashMap<>();
    /** 待新增任务队列，由外部线程投递，由 worker 自己合并到 activeTasks。 */
    private final Queue<ProcessTask> pendingAddTasks = new ConcurrentLinkedQueue<>();
    /** 待取消任务队列，由外部线程投递，由 worker 自己从 activeTasks 中移除。 */
    private final Queue<String> pendingCancelTaskIds = new ConcurrentLinkedQueue<>();
    private volatile Thread thread;
    private volatile boolean running;
    /** 当前 worker 进入空闲状态的起始时间，用于尾部缩容判断。 */
    private volatile long idleStartTime;

    ProcessWorker(int index, int capacity) {
        this.index = index;
        this.capacity = capacity;
    }

    int getIndex() {
        return index;
    }

    int getCapacity() {
        return capacity;
    }

    int taskCount() {
        return taskCount.get();
    }

    boolean isRunning() {
        return running;
    }

    Thread getThread() {
        return thread;
    }

    long getIdleStartTime() {
        return idleStartTime;
    }

    void setIdleStartTime(long idleStartTime) {
        this.idleStartTime = idleStartTime;
    }

    void clearIdle() {
        this.idleStartTime = 0L;
    }

    boolean hasActiveTasks() {
        return !activeTasks.isEmpty();
    }

    Collection<ProcessTask> activeTasksView() {
        return activeTasks.values();
    }

    /** 把新增任务投递给当前 worker。 */
    void enqueueAdd(ProcessTask task) {
        pendingAddTasks.add(task);
        taskCount.incrementAndGet();
        idleStartTime = 0L;
    }

    /** 把取消请求投递给当前 worker。 */
    void enqueueCancel(String taskId) {
        pendingCancelTaskIds.add(taskId);
        taskCount.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    /**
     * 将待新增/待取消队列合并到 activeTasks。
     *
     * <p>这个方法只应由 worker 自己线程调用。
     */
    void applyPendingChanges() {
        ProcessTask task;
        while ((task = pendingAddTasks.poll()) != null) {
            activeTasks.put(task.getTaskId(), task);
        }

        String taskId;
        while ((taskId = pendingCancelTaskIds.poll()) != null) {
            activeTasks.remove(taskId);
        }
    }

    /** 在真正没有任务时开始空闲计时。 */
    void markIdleIfNecessary() {
        if (taskCount.get() == 0 && idleStartTime == 0L) {
            idleStartTime = System.currentTimeMillis();
        }
    }

    /** 仅在当前 worker 尚未启动时创建并启动自己的线程。 */
    void startIfNecessary(Runnable loop, String threadName) {
        if (running) {
            return;
        }
        running = true;
        Thread newThread = new Thread(loop, threadName);
        newThread.setDaemon(true);
        thread = newThread;
        newThread.start();
    }

    void stop() {
        running = false;
    }

    /** worker 线程退出时回收线程引用并重置运行状态。 */
    void onLoopExit(Thread currentThread) {
        if (thread == currentThread) {
            thread = null;
        }
        running = false;
    }
}
