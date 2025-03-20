package com.example.easymrcp.sip;

import lombok.Data;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;

/**
 * SIP status per call
 */
@Data
public class SipSession {

    Dialog dialog;

    private ServerTransaction stx;

    RequestEvent requestEvent;
}
