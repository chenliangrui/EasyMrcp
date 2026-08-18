package com.cfsl.easymrcp.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CachedAudio {
    private final byte[] pcm;
    private final int charCount;
}
