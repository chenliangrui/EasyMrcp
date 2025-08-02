package com.cfsl.easymrcp.rtp;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过sip的DialogId管理当前系统sip与mrcp业务的关系对照表
 */
@Component
public class SipMrcpManage {
    private ConcurrentHashMap<String, String> sipRtpSessionMaps = new ConcurrentHashMap<>();

    public void addMrcpUuid(String dialogId, String uuid) {
        sipRtpSessionMaps.put(dialogId, uuid);
    }

    public String getMrcpUuid(String dialogId) {
        return sipRtpSessionMaps.get(dialogId);
    }

    public void removeMrcpUuid(String dialogId) {
        sipRtpSessionMaps.remove(dialogId);
    }
}
