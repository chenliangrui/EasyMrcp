package com.cfsl.easymrcp.tcp.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cfsl.easymrcp.asr.ASRConstant;
import com.cfsl.easymrcp.asr.AsrHandler;
import com.cfsl.easymrcp.mrcp.AsrCallback;
import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.MrcpEvent;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectSpeechEventHandlerTests {

    @Test
    void resultShouldSendStrictJsonWithProviderDuration() {
        String callId = "call-1";
        TestAsrHandler asrHandler = new TestAsrHandler();
        asrHandler.setAsrEngine("funasr");
        MrcpManage mrcpManage = mock(MrcpManage.class);
        when(mrcpManage.getAsrHandler(callId)).thenReturn(asrHandler);
        when(mrcpManage.getPushAsrRealtimeResult(callId)).thenReturn(false);
        TcpClientNotifier notifier = mock(TcpClientNotifier.class);
        DetectSpeechEventHandler eventHandler = new DetectSpeechEventHandler(mrcpManage);
        MrcpEvent event = new MrcpEvent(callId, null, TcpEventType.DetectSpeech,
                "{\"StartInputTimers\":false,\"AutomaticInterruption\":false}");

        eventHandler.handleEvent(event, notifier);
        AsrCallback callback = (AsrCallback) ReflectionTestUtils.getField(asrHandler, "callback");
        callback.apply(ASRConstant.Result, "识别结果", 12000L);

        ArgumentCaptor<String> dataCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).sendEvent(eq(callId), isNull(), eq(TcpEventType.RecognitionComplete),
                dataCaptor.capture());
        JSONObject payload = JSON.parseObject(dataCaptor.getValue());
        assertEquals("识别结果", payload.getString("text"));
        assertEquals("funasr", payload.getString("asrEngine"));
        assertEquals(Long.valueOf(12000L), payload.getLong("audioDurationMs"));
        assertEquals(3, payload.size());
    }

    private static final class TestAsrHandler extends AsrHandler {
        @Override
        public void create() {
        }

        @Override
        public void receive(byte[] pcmData) {
        }

        @Override
        public void sendEof() {
        }

        @Override
        public void asrClose() {
        }
    }
}
