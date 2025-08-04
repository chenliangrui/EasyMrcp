package com.cfsl.easymrcp.tcp;

import lombok.Data;

@Data
public class MrcpEventWithCallback{
    private Runnable runnable;
}
