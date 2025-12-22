package com.cfsl.easymrcp.rtp;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

/**
 * 音频编解码工具类
 * 根据SDP协商的Payload Type自动选择对应的编解码器
 * 
 * 支持的编码类型：
 * - Payload Type 0: G.711 μ-law (PCMU)
 * - Payload Type 8: G.711 A-law (PCMA)
 */
@Slf4j
public class AudioCodecUtil {
    
    /**
     * Payload Type常量定义
     */
    public static final int PT_PCMU = 0;  // G.711 μ-law
    public static final int PT_PCMA = 8;  // G.711 A-law
    
    /**
     * 根据Payload Type编码PCM数据
     * 
     * @param pcmData PCM音频数据
     * @param payloadType SDP协商的Payload Type (0=PCMU, 8=PCMA)
     * @return 编码后的数据
     */
    public static byte[] encode(byte[] pcmData, int payloadType) {
        switch (payloadType) {
            case PT_PCMU:
                return G711UUtil.encode(pcmData);
            case PT_PCMA:
                return G711AUtil.encode(pcmData);
            default:
                return G711AUtil.encode(pcmData);
        }
    }
    
    /**
     * 根据Payload Type编码PCM数据 (ByteBuf版本，零拷贝)
     * 
     * @param pcmData PCM音频数据ByteBuf
     * @param payloadType SDP协商的Payload Type (0=PCMU, 8=PCMA)
     * @return 编码后的ByteBuf
     */
    public static ByteBuf encode(ByteBuf pcmData, int payloadType) {
        switch (payloadType) {
            case PT_PCMU:
                return G711UUtil.encode(pcmData);
            case PT_PCMA:
                return G711AUtil.encode(pcmData);
            default:
                return G711AUtil.encode(pcmData);
        }
    }
    
    /**
     * 根据Payload Type解码音频数据
     * 
     * @param encodedData 编码数据
     * @param payloadType SDP协商的Payload Type (0=PCMU, 8=PCMA)
     * @return PCM解码后的数据
     */
    public static byte[] decode(byte[] encodedData, int payloadType) {
        switch (payloadType) {
            case PT_PCMU:
                return G711UUtil.decode(encodedData);
            case PT_PCMA:
                return G711AUtil.decode(encodedData);
            default:
                return G711AUtil.decode(encodedData);
        }
    }
    
    /**
     * 根据Payload Type解码音频数据 (ByteBuf版本，零拷贝)
     * 
     * @param encodedData 编码数据ByteBuf
     * @param payloadType SDP协商的Payload Type (0=PCMU, 8=PCMA)
     * @return PCM解码后的ByteBuf
     */
    public static ByteBuf decode(ByteBuf encodedData, int payloadType) {
        switch (payloadType) {
            case PT_PCMU:
                return G711UUtil.decode(encodedData);
            case PT_PCMA:
                return G711AUtil.decode(encodedData);
            default:
                return G711AUtil.decode(encodedData);
        }
    }
    
    /**
     * 获取编码名称
     * 
     * @param payloadType Payload Type
     * @return 编码名称
     */
    public static String getCodecName(int payloadType) {
        switch (payloadType) {
            case PT_PCMU:
                return "G.711 μ-law (PCMU)";
            case PT_PCMA:
                return "G.711 A-law (PCMA)";
            default:
                return "Unknown";
        }
    }
    
    /**
     * 从字符串解析Payload Type
     * 
     * @param payloadTypeStr Payload Type字符串 (如"0", "8")
     * @return Payload Type整数值，解析失败返回8(默认PCMA)
     */
    public static int parsePayloadType(String payloadTypeStr) {
        try {
            return Integer.parseInt(payloadTypeStr);
        } catch (NumberFormatException e) {
            return PT_PCMA;
        }
    }
}
