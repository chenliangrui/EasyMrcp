package com.cfsl.easymrcp.tts.xfyun;

import com.cfsl.easymrcp.domain.TtsConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "xfyun-tts")
@EqualsAndHashCode(callSuper = true)
@PropertySource(value = {"classpath:tts/xfyun-tts.properties", "file:tts/xfyun-tts.properties"}, ignoreResourceNotFound = true)
public class XfyunTtsConfig extends TtsConfig {
    // 地址与鉴权信息
    public String hostUrl;
    // 均到控制台-语音合成页面获取
    public String APPID;
    public String APISecret;
    public String APIKey;
    // 小语种必须使用UNICODE编码作为值
    public String TTE;
    // 发音人参数。到控制台-我的应用-语音合成-添加试用或购买发音人，添加后即显示该发音人参数值，若试用未添加的发音人会报错11200
    public String VCN;
}
