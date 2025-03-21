package com.example.easymrcp.rtp;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RtpTransmitter extends RtpConnection{
    @Override
    public void close() {

    }
}
