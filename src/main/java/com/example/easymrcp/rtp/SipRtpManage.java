package com.example.easymrcp.rtp;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过sip的DialogId管理当前系统建立的rtp通道
 */
@Component
public class SipRtpManage {
    private ConcurrentHashMap<String, RtpSession> sipRtpSessionMaps = new ConcurrentHashMap<>();

    public void addRtpSession(String dialogId, RtpSession rtpSession) {
        sipRtpSessionMaps.put(dialogId, rtpSession);
    }

    public RtpSession getRtpSession(String dialogId) {
        return sipRtpSessionMaps.get(dialogId);
    }

    public void removeRtpSession(String dialogId) {
        sipRtpSessionMaps.remove(dialogId);
    }
}
