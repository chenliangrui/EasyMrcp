package com.example.easymrcp.rtp;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class RtpTransmitter implements RtpConnection{
    @Override
    public void create() {

    }

    @Override
    public void close() {

    }

    @Override
    public String getChannelId() {
        return "";
    }

    @Override
    public void setChannelId(String channelId) {

    }
}
