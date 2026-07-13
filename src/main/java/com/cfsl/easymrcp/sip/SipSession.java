package com.cfsl.easymrcp.sip;

import lombok.Data;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import java.util.Vector;

/**
 * SIP status per call
 */
@Data
public class SipSession {

    private Dialog dialog;

    private ServerTransaction stx;

    private RequestEvent requestEvent;

    private boolean established;

    private boolean cancelled;

    // 记录首轮协商后的本地 RTP 端口，后续已建立会话的 re-INVITE
    // 只刷新 SIP session 时直接复用，避免重新建媒体链导致端口漂移。
    private int localRtpPort;

    // 记录首轮协商成功的 payload 列表，session refresh 回复时沿用原媒体参数，
    // 不再重新协商编码或重新初始化 ASR/TTS。
    private Vector<String> negotiatedAudioFormats;
}
