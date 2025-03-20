package com.example.easymrcp.sip;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIP status management for each call
 */
@Component
public class SipManage {
    private static final Map<String, SipSession> sipSessions = new ConcurrentHashMap<String, SipSession>();

    public boolean hasSipSession(String dialogId) {
        return sipSessions.containsKey(dialogId);
    }

    public void addSipSession(SipSession sipSession) {
        if (sipSession.getDialog() != null) {
            sipSessions.put(sipSession.getDialog().getDialogId(), sipSession);
        }
    }

    public SipSession getSipSession(String dialogId) {
        return sipSessions.get(dialogId);
    }

    public void removeSipSession(String dialogId) {
        sipSessions.remove(dialogId);
    }
}
