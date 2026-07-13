package com.cfsl.easymrcp.examples.esl;

import com.cfsl.easymrcp.examples.esl.config.EasyMrcpDemoProperties;
import io.netty.channel.EventLoopGroup;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SimpleEslCallListenerTests {

    private static final String ADDRESS = "127.0.0.1:8021";
    private static final String UUID = "call-uuid";

    @Test
    void startsHandlerBeforeAnsweringAndBridgingFirstValidPark() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler handler = mock(SimpleEslEasyMrcpHandler.class);
        TestCallListener listener = new TestCallListener(callController, handler);

        listener.handle(ADDRESS, parkEvent(UUID));

        InOrder order = inOrder(handler, callController);
        order.verify(handler).start();
        order.verify(callController).answerAndBridge(eq(ADDRESS), eq(UUID), any());
    }

    @Test
    void ignoresDuplicateParkForAnActiveUuid() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler firstHandler = mock(SimpleEslEasyMrcpHandler.class);
        SimpleEslEasyMrcpHandler secondHandler = mock(SimpleEslEasyMrcpHandler.class);
        TestCallListener listener = new TestCallListener(callController, firstHandler, secondHandler);

        listener.handle(ADDRESS, parkEvent(UUID));
        listener.handle(ADDRESS, parkEvent(UUID));

        assertEquals(1, listener.handlerCreations);
        verify(firstHandler).start();
        verify(secondHandler, never()).start();
        verify(callController).answerAndBridge(eq(ADDRESS), eq(UUID), any());
    }

    @Test
    void ignoresParkForTheBridgeLegCreatedByTheController() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler handler = mock(SimpleEslEasyMrcpHandler.class);
        TestCallListener listener = new TestCallListener(callController, handler);
        EslEvent event = parkEvent("bridge-leg-uuid");
        when(event.getEventHeaders().get("variable_easymrcp_bridge_leg")).thenReturn("true");

        listener.handle(ADDRESS, event);

        assertEquals(0, listener.handlerCreations);
        verifyNoInteractions(handler, callController);
    }

    @Test
    void ignoresCloseForAnUnknownUuid() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler handler = mock(SimpleEslEasyMrcpHandler.class);
        TestCallListener listener = new TestCallListener(callController, handler);

        listener.closeSession("unknown-uuid");

        assertEquals(0, listener.handlerCreations);
        verifyNoInteractions(handler, callController);
    }

    @Test
    void concurrentCloseWaitsForParkSetupBeforeClosingHandler() throws Exception {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler handler = mock(SimpleEslEasyMrcpHandler.class);
        CountDownLatch startEntered = new CountDownLatch(1);
        CountDownLatch allowStartReturn = new CountDownLatch(1);
        AtomicReference<Throwable> setupFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        doAnswer(invocation -> {
            startEntered.countDown();
            await(allowStartReturn, "park setup did not release handler.start()");
            return null;
        }).when(handler).start();
        TestCallListener listener = new TestCallListener(callController, handler);
        Thread setupThread = new Thread(() -> {
            try {
                listener.handle(ADDRESS, parkEvent(UUID));
            } catch (Throwable failure) {
                setupFailure.set(failure);
            }
        }, "esl-park-setup");
        Thread closeThread = new Thread(() -> {
            try {
                listener.closeSession(UUID);
            } catch (Throwable failure) {
                closeFailure.set(failure);
            }
        }, "esl-session-close");

        try {
            setupThread.start();
            assertTrue(startEntered.await(5, TimeUnit.SECONDS), "handler.start() was not entered");
            closeThread.start();
            awaitBlockedOn(closeThread, setupThread);
            assertTrue(closeThread.isAlive(), "closeSession() returned despite monitor contention");

            allowStartReturn.countDown();
            joinThread(setupThread);
            joinThread(closeThread);

            assertNull(setupFailure.get());
            assertNull(closeFailure.get());
            verify(handler, times(1)).close();
            verify(callController).answerAndBridge(eq(ADDRESS), eq(UUID), any());
        } finally {
            allowStartReturn.countDown();
            joinThreadQuietly(setupThread);
            joinThreadQuietly(closeThread);
        }
    }

    @Test
    void failureCallbackClosesSessionAndAllowsTheUuidToParkAgain() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler firstHandler = mock(SimpleEslEasyMrcpHandler.class);
        SimpleEslEasyMrcpHandler secondHandler = mock(SimpleEslEasyMrcpHandler.class);
        TestCallListener listener = new TestCallListener(callController, firstHandler, secondHandler);

        listener.handle(ADDRESS, parkEvent(UUID));

        ArgumentCaptor<Consumer<String>> failureCallback = failureCallbackCaptor();
        verify(callController).answerAndBridge(eq(ADDRESS), eq(UUID), failureCallback.capture());
        failureCallback.getValue().accept("uuid_bridge");

        verify(firstHandler).close();

        listener.handle(ADDRESS, parkEvent(UUID));

        verify(secondHandler).start();
        verify(callController, times(2)).answerAndBridge(eq(ADDRESS), eq(UUID), any());
    }

    @Test
    void staleFailureCallbackDoesNotCloseReplacementHandler() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler firstHandler = mock(SimpleEslEasyMrcpHandler.class);
        SimpleEslEasyMrcpHandler secondHandler = mock(SimpleEslEasyMrcpHandler.class);
        TestCallListener listener = new TestCallListener(callController, firstHandler, secondHandler);

        listener.handle(ADDRESS, parkEvent(UUID));

        ArgumentCaptor<Consumer<String>> failureCallback = failureCallbackCaptor();
        verify(callController).answerAndBridge(eq(ADDRESS), eq(UUID), failureCallback.capture());
        listener.closeSession(UUID);
        listener.handle(ADDRESS, parkEvent(UUID));
        failureCallback.getValue().accept("uuid_bridge");

        verify(firstHandler).close();
        verify(secondHandler, never()).close();
        verify(callController, times(2)).answerAndBridge(eq(ADDRESS), eq(UUID), any());
    }

    @Test
    void startFailureClosesFailedHandlerAndAllowsTheUuidToParkAgain() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler failedHandler = mock(SimpleEslEasyMrcpHandler.class);
        SimpleEslEasyMrcpHandler replacementHandler = mock(SimpleEslEasyMrcpHandler.class);
        RuntimeException failure = new RuntimeException("start failed");
        doThrow(failure).when(failedHandler).start();
        TestCallListener listener = new TestCallListener(callController, failedHandler, replacementHandler);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> listener.handle(ADDRESS, parkEvent(UUID)));

        assertSame(failure, thrown);
        verify(failedHandler).close();
        verifyNoInteractions(callController);

        listener.handle(ADDRESS, parkEvent(UUID));

        verify(replacementHandler).start();
        verify(callController).answerAndBridge(eq(ADDRESS), eq(UUID), any());
    }

    @Test
    void synchronousFailureCallbackAndControllerExceptionCloseHandlerOnlyOnce() {
        FreeSwitchCallController callController = mock(FreeSwitchCallController.class);
        SimpleEslEasyMrcpHandler failedHandler = mock(SimpleEslEasyMrcpHandler.class);
        SimpleEslEasyMrcpHandler replacementHandler = mock(SimpleEslEasyMrcpHandler.class);
        RuntimeException failure = new RuntimeException("call control failed");
        doAnswer(invocation -> {
            Consumer<String> onFailure = invocation.getArgument(2);
            onFailure.accept("uuid_bridge");
            throw failure;
        }).doNothing().when(callController).answerAndBridge(eq(ADDRESS), eq(UUID), any());
        TestCallListener listener = new TestCallListener(callController, failedHandler, replacementHandler);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> listener.handle(ADDRESS, parkEvent(UUID)));

        assertSame(failure, thrown);
        verify(failedHandler).start();
        verify(failedHandler, times(1)).close();

        listener.handle(ADDRESS, parkEvent(UUID));

        verify(replacementHandler).start();
        verify(callController, times(2)).answerAndBridge(eq(ADDRESS), eq(UUID), any());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Consumer<String>> failureCallbackCaptor() {
        return ArgumentCaptor.forClass(Consumer.class);
    }

    private static EslEvent parkEvent(String uuid) {
        EslEvent event = mock(EslEvent.class, RETURNS_DEEP_STUBS);
        when(event.getEventHeaders().get("Unique-ID")).thenReturn(uuid);
        return event;
    }

    private static void await(CountDownLatch latch, String timeoutMessage) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError(timeoutMessage);
        }
    }

    private static void joinThread(Thread thread) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(thread.isAlive(), thread.getName() + " did not finish");
    }

    private static void joinThreadQuietly(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            thread.interrupt();
        }
    }

    private static void awaitBlockedOn(Thread blockedThread, Thread lockOwnerThread) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        ThreadInfo threadInfo = null;
        while (System.nanoTime() < deadline) {
            threadInfo = threadMXBean.getThreadInfo(blockedThread.getId());
            if (threadInfo != null
                    && threadInfo.getThreadState() == Thread.State.BLOCKED
                    && threadInfo.getLockOwnerId() == lockOwnerThread.getId()) {
                return;
            }
            if (!blockedThread.isAlive()) {
                throw new AssertionError("closeSession() completed without blocking on park setup");
            }
            Thread.yield();
        }
        String observedState = threadInfo == null
                ? "no thread information"
                : threadInfo.getThreadState() + " owned by " + threadInfo.getLockOwnerId();
        throw new AssertionError("closeSession() did not block on park setup; observed " + observedState);
    }

    private static final class TestCallListener extends SimpleEslCallListener {
        private final Queue<SimpleEslEasyMrcpHandler> handlers;
        private int handlerCreations;

        private TestCallListener(FreeSwitchCallController callController, SimpleEslEasyMrcpHandler... handlers) {
            super(new EasyMrcpDemoProperties(), mock(EventLoopGroup.class), callController);
            this.handlers = new ArrayDeque<>(Arrays.asList(handlers));
        }

        @Override
        SimpleEslEasyMrcpHandler createHandler(String uuid) {
            handlerCreations++;
            return handlers.remove();
        }
    }
}
