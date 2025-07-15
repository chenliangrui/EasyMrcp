package com.cfsl.easymrcp.tts.example;

import com.cfsl.easymrcp.tts.TtsHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 压测示例代码
 */
@Slf4j
public class ExampleTtsProcessor extends TtsHandler {
    private ExampleTtsConfig exampleTtsConfig;

    public ExampleTtsProcessor(ExampleTtsConfig exampleTtsConfig) {
        this.exampleTtsConfig = exampleTtsConfig;
    }

    @Override
    public void create() {

    }

    @Override
    public void speak(String text) {
        new Thread(() -> {
            java.io.File wavFile = new java.io.File("/home/clr/Downloads/send-0.wav"); // text参数为wav文件路径
            try (java.io.FileInputStream fis = new java.io.FileInputStream(wavFile)) {
                // 跳过wav头部44字节
                if (fis.skip(44) != 44) {
                    throw new RuntimeException("WAV头部跳过失败");
                }
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    processor.putData(buffer, bytesRead);
                }
                // 发送结束标志
                processor.putData(com.cfsl.easymrcp.tts.TTSConstant.TTS_END_FLAG, com.cfsl.easymrcp.tts.TTSConstant.TTS_END_FLAG.length);
            } catch (Exception e) {
                log.error("发送失败: " + e.getMessage(), e);
            }
        }).start();
    }

    @Override
    public void ttsClose() {

    }
}
