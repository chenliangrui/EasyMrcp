package com.cfsl.easymrcp.examples.esl;

import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SimpleEslHangupEventHandlerTests {

    @Test
    void ignoresHangupForTheBridgeLegCreatedByTheController() {
        SimpleEslCallListener callListener = mock(SimpleEslCallListener.class);
        SimpleEslHangupEventHandler handler = new SimpleEslHangupEventHandler(callListener);
        EslEvent event = mock(EslEvent.class, RETURNS_DEEP_STUBS);
        when(event.getEventHeaders().get("Unique-ID")).thenReturn("bridge-leg-uuid");
        when(event.getEventHeaders().get("variable_easymrcp_bridge_leg")).thenReturn("true");

        handler.handle("127.0.0.1:8021", event);

        verifyNoInteractions(callListener);
    }
}
