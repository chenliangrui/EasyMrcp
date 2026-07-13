package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import org.junit.jupiter.api.Test;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandleAckTests {

    @Test
    void processAck_shouldMarkExistingSessionAsEstablished() {
        HandleAck handleAck = new HandleAck();
        SipManage sipManage = new SipManage();
        setField(handleAck, "sipManage", sipManage);

        Dialog dialog = mock(Dialog.class);
        when(dialog.getDialogId()).thenReturn("ack-dialog");

        SipSession session = new SipSession();
        session.setDialog(dialog);
        sipManage.addSipSession(session);

        RequestEvent requestEvent = mock(RequestEvent.class);
        when(requestEvent.getDialog()).thenReturn(dialog);

        handleAck.processAck(requestEvent);

        assertTrue(session.isEstablished());
        sipManage.removeSipSession("ack-dialog");
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
