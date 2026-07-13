package com.cfsl.easymrcp.tts.scheduler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RTP 发送 worker。
 *
 * <p>一个 worker 对应一个实际发送线程，独占管理自己的发送任务集合。
 * 外部线程不直接改 activeTasks，而是只投递新增/取消请求；
 * worker 再在自己的线程里合并请求、执行发送，并处理延迟释放。
 */
final class RtpWorker {
    private final int index;
    private final int capacity;
    /** 当前任务总数，用于扩缩容判断，不要求和 activeTasks 严格同一时刻一致。 */
    private final AtomicInteger taskCount = new AtomicInteger();
    /** 当前已生效、由 worker 自己线程直接遍历执行的发送任务集合。 */
    private final Map<String, AbstractRtpSendTask> activeTasks = new HashMap<>();
    /** 待新增发送任务队列。 */
    private final Queue<AbstractRtpSendTask> pendingAddTasks = new ConcurrentLinkedQueue<>();
    /** 待取消任务 ID 队列。 */
    private final Queue<String> pendingCancelTaskIds = new ConcurrentLinkedQueue<>();
    /** 已取消但还不能立刻释放的任务队列，由 worker 在安全点统一释放。 */
    private final Queue<AbstractRtpSendTask> pendingReleaseTasks = new ConcurrentLinkedQueue<>();
    private volatile Thread thread;
    private volatile boolean running;
    /** 当前 worker 进入空闲状态的起始时间，用于尾部缩容判断。 */
    private volatile long idleStartTime;

    RtpWorker(int index, int capacity) {
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

    Collection<AbstractRtpSendTask> activeTasksView() {
        return activeTasks.values();
    }

    /** 把新增发送任务投递给当前 worker。 */
    void enqueueAdd(AbstractRtpSendTask task) {
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
     * 将待新增/待取消请求合并到 activeTasks。
     *
     * <p>取消中的任务会先标记 canceled，再放入待释放队列，
     * 由 worker 在循环安全点统一释放，避免并发释放 ByteBuf。
     */
    void applyPendingChanges() {
        AbstractRtpSendTask task;
        while ((task = pendingAddTasks.poll()) != null) {
            activeTasks.put(task.getTaskId(), task);
        }

        String taskId;
        while ((taskId = pendingCancelTaskIds.poll()) != null) {
            AbstractRtpSendTask removed = activeTasks.remove(taskId);
            if (removed != null) {
                removed.markCanceled();
                pendingReleaseTasks.add(removed);
                continue;
            }
            AbstractRtpSendTask pendingTask = removePendingAdd(taskId);
            if (pendingTask != null) {
                pendingTask.markCanceled();
                pendingReleaseTasks.add(pendingTask);
            }
        }
    }

    /**
     * 在待新增队列中查找尚未生效的任务并移除。
     *
     * <p>用于处理“任务刚注册但尚未进入 activeTasks 就被取消”的场景。
     */
    private AbstractRtpSendTask removePendingAdd(String taskId) {
        java.util.ArrayList<AbstractRtpSendTask> retained = new java.util.ArrayList<>();
        AbstractRtpSendTask removed = null;
        AbstractRtpSendTask task;
        while ((task = pendingAddTasks.poll()) != null) {
            if (removed == null && task.getTaskId().equals(taskId)) {
                removed = task;
            } else {
                retained.add(task);
            }
        }
        for (AbstractRtpSendTask retainedTask : retained) {
            pendingAddTasks.add(retainedTask);
        }
        return removed;
    }

    /** 在 worker 自己的线程安全点统一释放已取消任务资源。 */
    void releasePendingTasks() {
        AbstractRtpSendTask task;
        while ((task = pendingReleaseTasks.poll()) != null) {
            task.release();
        }
    }

    /** 在真正没有任务时开始空闲计时。 */
    void markIdleIfNecessary() {
        if (taskCount.get() == 0 && idleStartTime == 0L) {
            idleStartTime = System.currentTimeMillis();
        }
    }

    /**
     * shutdown 时回收当前 worker 持有的全部任务资源。
     */
    void drainOwnedTasks(List<AbstractRtpSendTask> tasksToRelease) {
        AbstractRtpSendTask task;
        while ((task = pendingAddTasks.poll()) != null) {
            tasksToRelease.add(task);
        }
        tasksToRelease.addAll(activeTasks.values());
        activeTasks.clear();
        while ((task = pendingReleaseTasks.poll()) != null) {
            tasksToRelease.add(task);
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
