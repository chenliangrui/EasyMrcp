package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sip.Dialog;
import javax.sip.RequestEvent;

@Service
public class HandleAck {
    @Autowired
    private SipManage sipManage;

    public void processAck(RequestEvent requestEvent) {
        Dialog dialog = requestEvent.getDialog();
        if (dialog == null) {
            return;
        }
        SipSession session = sipManage.getSipSession(dialog.getDialogId());
        if (session != null) {
            session.setEstablished(true);
        }
    }
}
