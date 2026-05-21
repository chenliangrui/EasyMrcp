package com.cfsl.easymrcp.tts;

import com.cfsl.easymrcp.common.EMConstant;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.rtp.AudioCodecUtil;
import com.cfsl.easymrcp.tts.scheduler.TtsRtpScheduler;
import com.cfsl.easymrcp.utils.SipUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsRtpSchedulerTests {

    @Test
    void scheduler_shouldOnlyExposeUnifiedProcessorRegisterMethod() {
        long legacyProcessorOverloads = Arrays.stream(TtsRtpScheduler.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("register"))
                .filter(method -> Arrays.stream(method.getParameterTypes())
                        .anyMatch(type -> Set.of(
                                "NettyTtsRtpProcessor2",
                                "NettyTtsRtpProcessor3",
                                "NettyTtsRtpProcessor3Legacy",
                                "NettyTtsRtpProcessor4"
                        ).contains(type.getSimpleName())))
                .count();

        assertEquals(0, legacyProcessorOverloads);
    }

    @Test
    void rtpSendTask_shouldSendSingleFramePerTick_whenBufferHasMultipleFrames() throws Exception {
        setImmediateMrcpManage();
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9012, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
        EmbeddedChannel channel = new EmbeddedChannel();
        processor.setRtpChannel(channel);

        byte[] twoFrames = new byte[EMConstant.VOIP_SAMPLES_PER_FRAME * 2];
        for (int i = 0; i < twoFrames.length; i++) {
            twoFrames[i] = (byte) (i & 0x7F);
        }
        processor.getOutputRingBuffer().write(Unpooled.wrappedBuffer(twoFrames));

        Object task = createSendTask(processor, result -> {
        });
        invokeDoSendOnce(task, System.nanoTime());

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            byte[] actualPayload = new byte[packet.content().readableBytes() - 12];
            packet.content().getBytes(12, actualPayload);
            assertEquals(EMConstant.VOIP_SAMPLES_PER_FRAME, actualPayload.length);
            assertArrayEquals(Arrays.copyOfRange(twoFrames, 0, EMConstant.VOIP_SAMPLES_PER_FRAME), actualPayload);
        } finally {
            packet.release();
            processor.releaseResources();
            channel.finishAndReleaseAll();
        }

        assertEquals(EMConstant.VOIP_SAMPLES_PER_FRAME, processor.getOutputRingBuffer().getSize());
    }

    @Test
    void rtpSendTask_shouldPadPartialFrameToFullEncodedFrame() throws Exception {
        setImmediateMrcpManage();
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9013, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
        EmbeddedChannel channel = new EmbeddedChannel();
        processor.setRtpChannel(channel);

        byte[] partialFrame = new byte[80];
        Arrays.fill(partialFrame, (byte) 0x22);
        processor.getOutputRingBuffer().write(Unpooled.wrappedBuffer(partialFrame));

        Object task = createSendTask(processor, result -> {
        });
        invokeDoSendOnce(task, System.nanoTime());

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            byte[] actualPayload = new byte[packet.content().readableBytes() - 12];
            packet.content().getBytes(12, actualPayload);
            assertEquals(EMConstant.VOIP_SAMPLES_PER_FRAME, actualPayload.length);
            assertArrayEquals(partialFrame, Arrays.copyOfRange(actualPayload, 0, partialFrame.length));
            assertArrayEquals(new byte[EMConstant.VOIP_SAMPLES_PER_FRAME - partialFrame.length],
                    Arrays.copyOfRange(actualPayload, partialFrame.length, actualPayload.length));
        } finally {
            packet.release();
            processor.releaseResources();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rtpSendTask_shouldReplaceEndMarkerWithSilenceAndNotifyCompleted() throws Exception {
        setImmediateMrcpManage();
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9014, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
        EmbeddedChannel channel = new EmbeddedChannel();
        processor.setRtpChannel(channel);

        byte[] frame = new byte[EMConstant.VOIP_SAMPLES_PER_FRAME];
        Arrays.fill(frame, (byte) 0x33);
        frame[frame.length - 2] = TTSConstant.TTS_END_BYTE;
        frame[frame.length - 1] = TTSConstant.TTS_END_BYTE;
        processor.getOutputRingBuffer().write(Unpooled.wrappedBuffer(frame));

        List<String> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        Object task = createSendTask(processor, result -> {
            results.add(result);
            latch.countDown();
        });
        invokeDoSendOnce(task, System.nanoTime());

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            byte[] actualPayload = new byte[packet.content().readableBytes() - 12];
            packet.content().getBytes(12, actualPayload);
            assertEquals(EMConstant.VOIP_SAMPLES_PER_FRAME, actualPayload.length);
            assertEquals(TTSConstant.TTS_SILENCE_BYTE, actualPayload[actualPayload.length - 2]);
            assertEquals(TTSConstant.TTS_SILENCE_BYTE, actualPayload[actualPayload.length - 1]);
        } finally {
            packet.release();
            processor.releaseResources();
            channel.finishAndReleaseAll();
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(results.contains("completed"));
    }

    @Test
    void rtpSendTask_shouldReplaceInterruptMarkerWithSilenceAndNotifyInterrupt() throws Exception {
        setImmediateMrcpManage();
        NettyTtsRtpProcessor processor = new NettyTtsRtpProcessor("127.0.0.1", 9015, AudioCodecUtil.PT_PCMA, EMConstant.VOIP_SAMPLES_PER_FRAME, 20);
        EmbeddedChannel channel = new EmbeddedChannel();
        processor.setRtpChannel(channel);

        byte[] frame = new byte[EMConstant.VOIP_SAMPLES_PER_FRAME];
        Arrays.fill(frame, (byte) 0x44);
        frame[frame.length - 2] = TTSConstant.TTS_INTERRUPT_BYTE;
        frame[frame.length - 1] = TTSConstant.TTS_INTERRUPT_BYTE;
        processor.getOutputRingBuffer().write(Unpooled.wrappedBuffer(frame));

        List<String> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        Object task = createSendTask(processor, result -> {
            results.add(result);
            latch.countDown();
        });
        invokeDoSendOnce(task, System.nanoTime());

        DatagramPacket packet = channel.readOutbound();
        assertNotNull(packet);
        try {
            byte[] actualPayload = new byte[packet.content().readableBytes() - 12];
            packet.content().getBytes(12, actualPayload);
            assertEquals(EMConstant.VOIP_SAMPLES_PER_FRAME, actualPayload.length);
            assertEquals(TTSConstant.TTS_SILENCE_BYTE, actualPayload[actualPayload.length - 2]);
            assertEquals(TTSConstant.TTS_SILENCE_BYTE, actualPayload[actualPayload.length - 1]);
        } finally {
            packet.release();
            processor.releaseResources();
            channel.finishAndReleaseAll();
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(results.contains("interrupt"));
    }

    private void setImmediateMrcpManage() throws Exception {
        Field field = SipUtils.class.getDeclaredField("mrcpManage");
        field.setAccessible(true);
        field.set(null, new ImmediateMrcpManage());
    }

    private Object createSendTask(NettyTtsRtpProcessor processor, Consumer<String> callback) throws Exception {
        Class<?> taskClass = Class.forName("com.cfsl.easymrcp.tts.scheduler.RtpSendTask");
        Constructor<?> constructor = taskClass.getDeclaredConstructor(String.class, NettyTtsRtpProcessor.class, Consumer.class);
        constructor.setAccessible(true);
        return constructor.newInstance("task-1", processor, callback);
    }

    private void invokeDoSendOnce(Object task, long nowNanos) throws Exception {
        Method method = task.getClass().getDeclaredMethod("doSendOnce", long.class);
        method.setAccessible(true);
        method.invoke(task, nowNanos);
    }

    private static class ImmediateMrcpManage extends MrcpManage {
        @Override
        public void executeTask(Runnable runnable) {
            runnable.run();
        }
    }
}
