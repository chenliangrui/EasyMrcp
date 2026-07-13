package com.cfsl.easymrcp.sip;

import com.cfsl.easymrcp.sip.handle.HandleAck;
import com.cfsl.easymrcp.sip.handle.HandleBye;
import com.cfsl.easymrcp.sip.handle.HandleCancel;
import com.cfsl.easymrcp.sip.handle.HandleInvite;
import com.cfsl.easymrcp.sip.handle.HandleOptions;
import org.junit.jupiter.api.Test;

import javax.sip.RequestEvent;
import javax.sip.message.Request;
import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SipListenerImplTests {

    @Test
    void processRequest_shouldDispatchCancelToHandleCancel() {
        SipListenerImpl listener = new SipListenerImpl();
        HandleCancel handleCancel = mock(HandleCancel.class);

        setField(listener, "handleInvite", mock(HandleInvite.class));
        setField(listener, "handleAck", mock(HandleAck.class));
        setField(listener, "handleBye", mock(HandleBye.class));
        setField(listener, "handleOptions", mock(HandleOptions.class));
        setField(listener, "handleCancel", handleCancel);

        Request request = mock(Request.class);
        RequestEvent requestEvent = mock(RequestEvent.class);
        when(requestEvent.getRequest()).thenReturn(request);
        when(request.getMethod()).thenReturn(Request.CANCEL);

        listener.processRequest(requestEvent);

        verify(handleCancel).processCancel(requestEvent);
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
