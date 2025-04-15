package com.example.easymrcp.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AsrConfig extends BaseConfig {
    protected String identifyPatterns;
}
