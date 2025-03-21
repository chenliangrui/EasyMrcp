package com.example.easymrcp.rtp;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理每通电话的rtp通道
 */
@Data
public class RtpSession {
    private String id;
    private Map<String, RtpConnection> channelMaps;

    public RtpSession(String dialogId) {
        this.id = dialogId;
        this.channelMaps = new ConcurrentHashMap<>();
    }

    public void addChannel(String channelId, RtpConnection rtpConnection) {
        channelMaps.put(channelId, rtpConnection);
    }
}
