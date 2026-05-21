package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.rtp.AudioCodecUtil;
import com.cfsl.easymrcp.tts.scheduler.TtsProcessScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsProcessSchedulerTests {

    @Test
    void register_shouldCreateWorkerLazilyAndExecuteProcessor() throws Exception {
        TtsProcessScheduler scheduler = newScheduler(1, 0.8d, 80L, 0, 4, 5);
        CountDownLatch latch = new CountDownLatch(1);
        CountingProcessor processor = new CountingProcessor(latch);
        try {
            assertEquals(0, workerCount(scheduler));

            String taskId = scheduler.register(processor);

            assertTrue(waitUntil(() -> workerCount(scheduler) == 1, 1500));
            assertEquals(0, taskOwnerIndex(scheduler, taskId));
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(processor.invocationCount.get() > 0);
        } finally {
            scheduler.shutdown();
            processor.releaseResources();
        }
    }

    @Test
    void register_shouldCreateSecondWorkerWhenFirstWorkerReachesExpandLimit() throws Exception {
        TtsProcessScheduler scheduler = newScheduler(1, 0.8d, 200L, 0, 4, 5);
        CountingProcessor firstProcessor = new CountingProcessor(new CountDownLatch(1));
        CountingProcessor secondProcessor = new CountingProcessor(new CountDownLatch(1));
        try {
            String firstTaskId = scheduler.register(firstProcessor);
            String secondTaskId = scheduler.register(secondProcessor);

            assertTrue(waitUntil(() -> workerCount(scheduler) == 2, 1500));
            assertEquals(0, taskOwnerIndex(scheduler, firstTaskId));
            assertEquals(1, taskOwnerIndex(scheduler, secondTaskId));
        } finally {
            scheduler.shutdown();
            firstProcessor.releaseResources();
            secondProcessor.releaseResources();
        }
    }

    @Test
    void register_shouldUseExistingWorkerCapacityWhenGlobalCapacityChanges() throws Exception {
        TtsProcessScheduler scheduler = newScheduler(2, 0.8d, 200L, 0, 4, 5);
        CountingProcessor firstProcessor = new CountingProcessor(new CountDownLatch(1));
        CountingProcessor secondProcessor = new CountingProcessor(new CountDownLatch(1));
        try {
            String firstTaskId = scheduler.register(firstProcessor);
            assertTrue(waitUntil(() -> workerCount(scheduler) == 1, 1500));

            setSchedulerField(scheduler, "workerCapacity", 1);
            String secondTaskId = scheduler.register(secondProcessor);

            assertEquals(1, workerCount(scheduler));
            assertEquals(0, taskOwnerIndex(scheduler, firstTaskId));
            assertEquals(0, taskOwnerIndex(scheduler, secondTaskId));
        } finally {
            scheduler.shutdown();
            firstProcessor.releaseResources();
            secondProcessor.releaseResources();
        }
    }

    @Test
    void cancel_shouldShrinkOnlyTailWorkerAfterIdleTimeout() throws Exception {
        TtsProcessScheduler scheduler = newScheduler(1, 0.8d, 50L, 0, 4, 5);
        CountingProcessor firstProcessor = new CountingProcessor(new CountDownLatch(1));
        CountingProcessor secondProcessor = new CountingProcessor(new CountDownLatch(1));
        String firstTaskId = null;
        String secondTaskId = null;
        try {
            firstTaskId = scheduler.register(firstProcessor);
            secondTaskId = scheduler.register(secondProcessor);
            assertTrue(waitUntil(() -> workerCount(scheduler) == 2, 1500));

            scheduler.cancel(secondTaskId);

            assertTrue(waitUntil(() -> workerCount(scheduler) == 1, 2000));
            assertTrue(hasWorker(scheduler, 0));
            assertTrue(!hasWorker(scheduler, 1));
            assertEquals(0, taskOwnerIndex(scheduler, firstTaskId));
        } finally {
            if (firstTaskId != null) {
                scheduler.cancel(firstTaskId);
            }
            scheduler.shutdown();
            firstProcessor.releaseResources();
            secondProcessor.releaseResources();
        }
    }

    private TtsProcessScheduler newScheduler(int workerCapacity,
                                             double expandThreshold,
                                             long idleTimeoutMs,
                                             int minWorkers,
                                             int maxWorkers,
                                             int processIntervalMs) throws Exception {
        TtsProcessScheduler scheduler = new TtsProcessScheduler();
        setField(scheduler, "workerCapacity", workerCapacity);
        setField(scheduler, "expandThreshold", expandThreshold);
        setField(scheduler, "idleTimeoutMs", idleTimeoutMs);
        setField(scheduler, "minWorkers", minWorkers);
        setField(scheduler, "maxWorkers", maxWorkers);
        setField(scheduler, "processIntervalMs", processIntervalMs);
        return scheduler;
    }

    private int workerCount(TtsProcessScheduler scheduler) {
        return withLifecycleLock(scheduler, () -> getWorkers(scheduler).size());
    }

    private boolean hasWorker(TtsProcessScheduler scheduler, int index) {
        return withLifecycleLock(scheduler, () -> getWorkers(scheduler).containsKey(index));
    }

    private Integer taskOwnerIndex(TtsProcessScheduler scheduler, String taskId) {
        return withLifecycleLock(scheduler, () -> getTaskOwnerIndexMap(scheduler).get(taskId));
    }

    private void setSchedulerField(TtsProcessScheduler scheduler, String fieldName, Object value) {
        withLifecycleLock(scheduler, () -> {
            setFieldUnchecked(scheduler, fieldName, value);
            return null;
        });
    }

    private <T> T withLifecycleLock(TtsProcessScheduler scheduler, Supplier<T> action) {
        Object lifecycleLock = getFieldUnchecked(scheduler, "lifecycleLock");
        synchronized (lifecycleLock) {
            return action.get();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object> getWorkers(TtsProcessScheduler scheduler) {
        return (Map<Integer, Object>) getFieldUnchecked(scheduler, "workers");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> getTaskOwnerIndexMap(TtsProcessScheduler scheduler) {
        return (Map<String, Integer>) getFieldUnchecked(scheduler, "taskOwnerIndexMap");
    }

    private Object getFieldUnchecked(Object target, String fieldName) {
        try {
            return getField(target, fieldName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setFieldUnchecked(Object target, String fieldName, Object value) {
        try {
            setField(target, fieldName, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private boolean waitUntil(BooleanSupplier supplier, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (supplier.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20L);
        }
        return supplier.getAsBoolean();
    }

    private static class CountingProcessor extends NettyTtsRtpProcessor {
        private final CountDownLatch latch;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private CountingProcessor(CountDownLatch latch) throws Exception {
            super("127.0.0.1", 9200, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
            this.latch = latch;
        }

        @Override
        public void processOnce() {
            invocationCount.incrementAndGet();
            latch.countDown();
        }
    }
}
