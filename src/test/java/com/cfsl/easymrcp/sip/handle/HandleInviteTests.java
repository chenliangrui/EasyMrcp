package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sdp.MediaDescription;
import javax.sip.message.Request;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandleInviteTests {

    @Test
    void handleInvite_shouldReuseExistingSessionForReinvite() throws Exception {
        HandleInvite handleInvite = new HandleInvite();
        SipManage sipManage = new SipManage();
        SipContext sipContext = mock(SipContext.class);
        HandleSipInit handleSipInit = mock(HandleSipInit.class);
        HandleOk handleOk = mock(HandleOk.class);

        setField(handleInvite, "sipContext", sipContext);
        setField(handleInvite, "sipManage", sipManage);
        setField(handleInvite, "handleSipInit", handleSipInit);
        setField(handleInvite, "handleOk", handleOk);
        when(sipContext.getSipServerIp()).thenReturn("127.0.0.1");

        Dialog dialog = mock(Dialog.class);
        when(dialog.getDialogId()).thenReturn("reinvite-dialog");

        SipSession existingSession = new SipSession();
        existingSession.setDialog(dialog);
        sipManage.addSipSession(existingSession);

        Request request = mock(Request.class);
        RequestEvent requestEvent = mock(RequestEvent.class);
        ServerTransaction serverTransaction = mock(ServerTransaction.class);
        when(requestEvent.getRequest()).thenReturn(request);
        when(requestEvent.getServerTransaction()).thenReturn(serverTransaction);
        when(requestEvent.getDialog()).thenReturn(dialog);
        when(request.getRawContent()).thenReturn(minimalSdp().getBytes(StandardCharsets.UTF_8));

        when(handleSipInit.initAsrAndTts(any(SdpMessage.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        handleInvite.handleInvite(requestEvent);

        verify(handleOk).sendResponse(same(existingSession), any(SdpMessage.class));
        assertSame(serverTransaction, existingSession.getStx());
        assertSame(requestEvent, existingSession.getRequestEvent());

        sipManage.removeSipSession("reinvite-dialog");
    }

    @Test
    void handleInvite_shouldOnlyRefreshEstablishedSessionForReinvite() throws Exception {
        HandleInvite handleInvite = new HandleInvite();
        SipManage sipManage = new SipManage();
        SipContext sipContext = mock(SipContext.class);
        HandleSipInit handleSipInit = mock(HandleSipInit.class);
        HandleOk handleOk = mock(HandleOk.class);

        setField(handleInvite, "sipContext", sipContext);
        setField(handleInvite, "sipManage", sipManage);
        setField(handleInvite, "handleSipInit", handleSipInit);
        setField(handleInvite, "handleOk", handleOk);
        when(sipContext.getSipServerIp()).thenReturn("127.0.0.1");

        Dialog dialog = mock(Dialog.class);
        when(dialog.getDialogId()).thenReturn("refresh-dialog");

        SipSession existingSession = new SipSession();
        existingSession.setDialog(dialog);
        existingSession.setEstablished(true);
        existingSession.setLocalRtpPort(20001);
        Vector<String> formats = new Vector<>();
        formats.add("102");
        existingSession.setNegotiatedAudioFormats(formats);
        sipManage.addSipSession(existingSession);

        Request request = mock(Request.class);
        RequestEvent requestEvent = mock(RequestEvent.class);
        ServerTransaction serverTransaction = mock(ServerTransaction.class);
        when(requestEvent.getRequest()).thenReturn(request);
        when(requestEvent.getServerTransaction()).thenReturn(serverTransaction);
        when(requestEvent.getDialog()).thenReturn(dialog);
        when(request.getRawContent()).thenReturn(minimalSdp().getBytes(StandardCharsets.UTF_8));

        handleInvite.handleInvite(requestEvent);

        verify(handleSipInit, never()).initAsrAndTts(any(SdpMessage.class), any(), any());

        ArgumentCaptor<SdpMessage> sdpCaptor = ArgumentCaptor.forClass(SdpMessage.class);
        verify(handleOk).sendResponse(same(existingSession), sdpCaptor.capture());
        MediaDescription mediaDescription = sdpCaptor.getValue().getRtpChannels().get(0);
        assertEquals(20001, mediaDescription.getMedia().getMediaPort());
        assertEquals("127.0.0.1", sdpCaptor.getValue().getSessionDescription().getConnection().getAddress());
        assertSame(serverTransaction, existingSession.getStx());
        assertSame(requestEvent, existingSession.getRequestEvent());

        sipManage.removeSipSession("refresh-dialog");
    }

    private static String minimalSdp() {
        return "v=0\r\n"
                + "o=- 0 0 IN IP4 127.0.0.1\r\n"
                + "s=-\r\n"
                + "c=IN IP4 127.0.0.1\r\n"
                + "t=0 0\r\n"
                + "m=audio 49170 RTP/AVP 0\r\n"
                + "a=rtpmap:0 PCMU/8000\r\n";
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
