package com.cfsl.easymrcp.testutils;

import javax.sound.sampled.*;
import java.io.*;
public class PCMRecorder {
    private static final int SAMPLE_RATE = 24000;  // 44.1kHz
    private static final int SAMPLE_SIZE_BITS = 16; // 16位样本
    private static final int CHANNELS = 1;         // 单声道
    private static final boolean SIGNED = true;    // 有符号样本
    private static final boolean BIG_ENDIAN = false; // 小端序
    private volatile boolean isRecording = false;
    public static void main(String[] args) {
        new PCMRecorder().startRecording();
    }
    public void startRecording() {
        try {
            // 设置音频格式
            AudioFormat format = new AudioFormat(
                    SAMPLE_RATE,
                    SAMPLE_SIZE_BITS,
                    CHANNELS,
                    SIGNED,
                    BIG_ENDIAN);
            // 获取目标数据线（麦克风）
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);

            System.out.println("开始录音... (按Enter键停止)");
            line.start();
            // 创建输出文件
            File outputFile = new File("D:\\code\\network-manage\\ruoyi-admin\\src\\main\\java\\output.pcm");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            isRecording = true;
            // 创建录音线程
            Thread recordingThread = new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(line)) {
                    byte[] buffer = new byte[1024];
                    while (isRecording) {
                        int count = ais.read(buffer, 0, buffer.length);
                        if (count > 0) {
                            out.write(buffer, 0, count);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            recordingThread.start();
            // 等待用户输入停止
            System.in.read();
            System.out.println("停止录音...");
            isRecording = false;
            // 保存录音到文件
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                out.writeTo(fos);
            }
            line.stop();
            line.close();
            System.out.println("录音已保存至: " + outputFile.getAbsolutePath());
        } catch (LineUnavailableException | IOException e) {
            e.printStackTrace();
        }
    }
}