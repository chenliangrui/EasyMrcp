package com.example.easymrcp.rtp;

import lombok.Data;

@Data
public abstract class RtpConnection {
    String channelId;

    public abstract void close();
}
