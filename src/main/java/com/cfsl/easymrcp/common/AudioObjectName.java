package com.cfsl.easymrcp.common;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class AudioObjectName {
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "^.+_v(\\d+)_c(\\d+)_s(\\d+)_([0-9a-fA-F]{64})\\.wav$");

    private final String objectName;
    private final long version;
    private final int charCount;
    private final long size;
    private final String sha256;

    private AudioObjectName(String objectName, long version, int charCount, long size, String sha256) {
        this.objectName = objectName;
        this.version = version;
        this.charCount = charCount;
        this.size = size;
        this.sha256 = sha256.toLowerCase();
    }

    public static AudioObjectName parse(String objectName, String allowedPrefix) {
        if (objectName == null || allowedPrefix == null || !objectName.startsWith(allowedPrefix)
                || objectName.contains("..") || objectName.contains("\\")) {
            throw new IllegalArgumentException("非法录音 objectName");
        }
        String fileName = objectName.substring(objectName.lastIndexOf('/') + 1);
        Matcher matcher = FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("录音文件名格式错误");
        }
        return new AudioObjectName(objectName, Long.parseLong(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Long.parseLong(matcher.group(3)), matcher.group(4));
    }
}
