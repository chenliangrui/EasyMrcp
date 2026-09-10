package com.cfsl.easymrcp.tcp.handler;

import com.cfsl.easymrcp.mrcp.MrcpManage;
import com.cfsl.easymrcp.tcp.MrcpEvent;
import com.cfsl.easymrcp.tcp.TcpClientNotifier;
import com.cfsl.easymrcp.tcp.TcpEventType;
import com.cfsl.easymrcp.tcp.TcpResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ClientConnectEventHandlerTests {

    @Test
    void clientConnectShouldSaveAsrEngineForCurrentCall() {
        MrcpManage mrcpManage = mock(MrcpManage.class);
        ClientConnectEventHandler handler = new ClientConnectEventHandler(mrcpManage);
        MrcpEvent event = new MrcpEvent("call-1", null, TcpEventType.ClientConnect,
                "{\"AsrEngine\":\"funasr\"}");

        handler.handleEvent(event, mock(TcpClientNotifier.class));

        verify(mrcpManage).updateConnection("call-1", null, "funasr");
    }

    @Test
    void spyConnectWithoutAsrEngineShouldReturnErrorAndSkipConnectionSetup() {
        MrcpManage mrcpManage = mock(MrcpManage.class);
        ClientConnectEventHandler handler = new ClientConnectEventHandler(mrcpManage);
        MrcpEvent event = new MrcpEvent("call-1", null, TcpEventType.ClientConnect,
                "{\"Type\":\"spy\"}");

        TcpResponse response = handler.handleEvent(event, mock(TcpClientNotifier.class));

        assertEquals(500, response.getCode());
        verify(mrcpManage, never()).updateConnection(anyString(), any(), any());
    }
}
