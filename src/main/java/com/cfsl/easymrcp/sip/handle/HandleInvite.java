package com.cfsl.easymrcp.sip.handle;

import com.cfsl.easymrcp.common.SipContext;
import com.cfsl.easymrcp.sdp.SdpMessage;
import com.cfsl.easymrcp.sip.SipManage;
import com.cfsl.easymrcp.sip.SipSession;
import com.cfsl.easymrcp.utils.SipUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sdp.*;
import javax.sip.*;
import javax.sip.header.ExtensionHeader;
import javax.sip.header.Header;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.List;

@Slf4j
@Service
public class HandleInvite {
    @Autowired
    private SipContext sipContext;
    @Autowired
    SipManage sipManage;
    @Autowired
    HandleSipInit handleSipInit;
    @Autowired
    HandleOk handleOk;

    public void handleInvite(RequestEvent requestEvent) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        String guid = SipUtils.getGUID();

        // 解析自定义头部 X-EasyMRCP
        String customHeaderUUID = null;
        Header customHeader = request.getHeader("X-EasyMRCP");
        if (customHeader != null) {
            if (customHeader instanceof ExtensionHeader) {
                customHeaderUUID = ((ExtensionHeader) customHeader).getValue();
            } else {
                customHeaderUUID = customHeader.toString().substring(customHeader.toString().indexOf(":") + 1).trim();
            }
            log.info("Received X-EasyMRCP with value: {}", customHeaderUUID);
        }
        
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                // 解决freeswitch没有Contact问题
                request.setHeader(sipContext.getContactHeader("xiaohua", "1.1.1.1"));
                st = sipProvider.getNewServerTransaction(request);
            }

            byte[] rawContent = request.getRawContent();
            SdpFactory sdpFactory = SdpFactory.getInstance();
            if (rawContent == null) {
                log.warn("no offer in initAsrAndTts request");
            } else {
                SipSession sipSession = resolveSipSession(requestEvent, request, st, guid);
                String contentString = new String(rawContent);
                SessionDescription sessionDescription = sdpFactory.createSessionDescription(contentString);
                SdpMessage sdpSessionMessage = SdpMessage.createSdpSessionMessage(sessionDescription);
                SdpMessage invite;
                // 已建立会话后的 re-INVITE 只允许刷新 SIP session，
                // 不能重新初始化媒体链，否则会把既有 RTP 端口和 TTS/ASR 会话打乱。
                if (shouldRefreshSessionOnly(sipSession)) {
                    invite = refreshSession(sdpSessionMessage, sipSession);
                } else {
                    invite = invite(sdpSessionMessage, sipSession, customHeaderUUID);
                }
                try {
                    handleOk.sendResponse(sipSession, invite);
                } catch (SipException e) {
                    log.warn("error processing bye: " + e.getMessage(), e);
                    throw new SdpException(e.getMessage(), e);
                }
            }
        } catch (TransactionAlreadyExistsException | TransactionUnavailableException | ParseException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (SdpException e) {
            throw new RuntimeException(e);
        }


    }

    private SipSession resolveSipSession(RequestEvent requestEvent, Request request, ServerTransaction st, String guid)
            throws ParseException {
        Dialog dialog = requestEvent.getDialog();
        if (dialog == null) {
            // 首个 INVITE 需要先建立 early dialog，后续才能把会话挂到 dialogId 上管理。
            Response response = sipContext.getMessageFactory().createResponse(Response.RINGING, request);
            ToHeader provToHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            provToHeader.setTag(guid);
            try {
                st.sendResponse(response);
            } catch (SipException | InvalidArgumentException e) {
                log.error("send Trying error:{}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
            dialog = st.getDialog();
        }

        SipSession sipSession = dialog == null ? null : sipManage.getSipSession(dialog.getDialogId());
        if (sipSession == null) {
            sipSession = new SipSession();
        }

        sipSession.setDialog(dialog);
        sipSession.setStx(st);
        sipSession.setRequestEvent(requestEvent);
        sipManage.addSipSession(sipSession);
        return sipSession;
    }

    private boolean shouldRefreshSessionOnly(SipSession sipSession) {
        return sipSession != null
                && sipSession.isEstablished()
                && sipSession.getLocalRtpPort() > 0
                && sipSession.getNegotiatedAudioFormats() != null
                && !sipSession.getNegotiatedAudioFormats().isEmpty();
    }

    private SdpMessage refreshSession(SdpMessage sdpMessage, SipSession session) throws SdpException {
        // 这里复用首轮协商成功的 RTP 端口和编码列表，只回一个等价 SDP，
        // 让对端完成 session refresh，而不是触发新的媒体初始化。
        List<MediaDescription> channels = sdpMessage.getRtpChannels();
        if (!channels.isEmpty()) {
            List<MediaDescription> rtpmd = sdpMessage.getAudioChansForThisControlChan(channels.get(0));
            if (!rtpmd.isEmpty()) {
                rtpmd.get(0).getMedia().setMediaFormats(new java.util.Vector<>(session.getNegotiatedAudioFormats()));
                rtpmd.get(0).getMedia().setMediaPort(session.getLocalRtpPort());
            }
        }
        sdpMessage.setSessionAddress(sipContext.getSipServerIp());
        return sdpMessage;
    }

    private SdpMessage invite(SdpMessage sdpMessage, SipSession session, String customHeaderUUID) throws SdpException {
        sdpMessage = handleSipInit.initAsrAndTts(sdpMessage, session, customHeaderUUID);
        sdpMessage.getSessionDescription().getConnection().setAddress(sipContext.getSipServerIp());
        return sdpMessage;
    }
}
