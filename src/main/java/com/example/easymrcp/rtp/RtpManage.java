package com.example.easymrcp.rtp;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理系统rtp通道
 */
@Component
public class RtpManage {
    private ConcurrentHashMap<String, RtpSession> rtpSessionMaps = new ConcurrentHashMap<>();

    public void addRtpSession(String rtpSessionId, RtpSession rtpSession) {
        rtpSessionMaps.put(rtpSessionId, rtpSession);
    }

    public RtpSession getRtpSession(String rtpSessionId) {
        return rtpSessionMaps.get(rtpSessionId);
    }

    public void removeRtpSession(String rtpSessionId) {
        rtpSessionMaps.remove(rtpSessionId);
    }
}
