package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.rtp.AudioCodecUtil;
import com.cfsl.easymrcp.tts.scheduler.TtsRtpScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsRtpSchedulerDynamicTests {

    @Test
    void register_shouldCreateWorkersLazilyAndSplitTasksWhenThresholdIsReached() throws Exception {
        TtsRtpScheduler scheduler = newScheduler(1, 0.8d, 80L, 0, 4, 5);
        NettyTtsRtpProcessor firstProcessor = newProcessor(9301);
        NettyTtsRtpProcessor secondProcessor = newProcessor(9302);
        try {
            assertEquals(0, workers(scheduler).size());

            String firstTaskId = scheduler.register(firstProcessor, result -> { });
            String secondTaskId = scheduler.register(secondProcessor, result -> { });

            assertTrue(waitUntil(() -> workers(scheduler).size() == 2, 500));
            assertEquals(0, taskOwnerIndexMap(scheduler).get(firstTaskId));
            assertEquals(1, taskOwnerIndexMap(scheduler).get(secondTaskId));
        } finally {
            scheduler.shutdown();
            firstProcessor.releaseResources();
            secondProcessor.releaseResources();
        }
    }

    @Test
    void cancel_shouldShrinkOnlyTailWorkerAfterIdleTimeout() throws Exception {
        TtsRtpScheduler scheduler = newScheduler(1, 0.8d, 50L, 0, 4, 5);
        NettyTtsRtpProcessor firstProcessor = newProcessor(9303);
        NettyTtsRtpProcessor secondProcessor = newProcessor(9304);
        String firstTaskId = null;
        try {
            firstTaskId = scheduler.register(firstProcessor, result -> { });
            String secondTaskId = scheduler.register(secondProcessor, result -> { });
            assertTrue(waitUntil(() -> workers(scheduler).size() == 2, 500));

            scheduler.cancel(secondTaskId);

            assertTrue(waitUntil(() -> workers(scheduler).size() == 1, 1000));
            assertTrue(workers(scheduler).containsKey(0));
            assertTrue(!workers(scheduler).containsKey(1));
            assertEquals(0, taskOwnerIndexMap(scheduler).get(firstTaskId));
        } finally {
            if (firstTaskId != null) {
                scheduler.cancel(firstTaskId);
            }
            scheduler.shutdown();
            firstProcessor.releaseResources();
            secondProcessor.releaseResources();
        }
    }

    private TtsRtpScheduler newScheduler(int workerCapacity,
                                         double expandThreshold,
                                         long idleTimeoutMs,
                                         int minWorkers,
                                         int maxWorkers,
                                         int sendIntervalMs) throws Exception {
        TtsRtpScheduler scheduler = new TtsRtpScheduler();
        setField(scheduler, "workerCapacity", workerCapacity);
        setField(scheduler, "expandThreshold", expandThreshold);
        setField(scheduler, "idleTimeoutMs", idleTimeoutMs);
        setField(scheduler, "minWorkers", minWorkers);
        setField(scheduler, "maxWorkers", maxWorkers);
        setField(scheduler, "sendIntervalMs", sendIntervalMs);
        return scheduler;
    }

    private NettyTtsRtpProcessor newProcessor(int port) throws Exception {
        return new NettyTtsRtpProcessor("127.0.0.1", port, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object> workers(TtsRtpScheduler scheduler) {
        try {
            return (Map<Integer, Object>) getField(scheduler, "workers");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> taskOwnerIndexMap(TtsRtpScheduler scheduler) {
        try {
            return (Map<String, Integer>) getField(scheduler, "taskOwnerIndexMap");
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
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (supplier.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10L);
        }
        return supplier.getAsBoolean();
    }
}
