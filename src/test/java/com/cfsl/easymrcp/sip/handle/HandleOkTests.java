package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sip.SipSession;
import org.junit.jupiter.api.Test;

import javax.sip.message.MessageFactory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class HandleOkTests {

    @Test
    void sendResponse_shouldRejectCancelledSession() {
        HandleOk handleOk = new HandleOk();
        SipContext sipContext = mock(SipContext.class);
        sipContext.messageFactory = mock(MessageFactory.class);
        handleOk.sipContext = sipContext;

        SipSession session = new SipSession();
        session.setCancelled(true);

        assertThrows(IllegalStateException.class, () -> handleOk.sendResponse(session, null));
    }
}
