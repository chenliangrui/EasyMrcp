package com.cfsl.easymrcp.testutils;

import javax.sound.sampled.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VoIP PCM数据接收器 - 静态工具类
 * 用于接收8kHz的VoIP PCM数据并保存为WAV格式
 * 
 * @author EasyMRCP
 */
public class VoipPcmRecorder {
    
    // 音频格式参数 - 8kHz VoIP标准
    private static final int SAMPLE_RATE = 8000;        // 8kHz采样率
    private static final int SAMPLE_SIZE_BITS = 16;     // 16位样本
    private static final int CHANNELS = 1;              // 单声道
    private static final boolean SIGNED = true;         // 有符号样本
    private static final boolean BIG_ENDIAN = false;    // 小端序
    
    // 静态变量
    private static ByteArrayOutputStream pcmDataBuffer = new ByteArrayOutputStream();
    private static final AudioFormat audioFormat = new AudioFormat(
        SAMPLE_RATE,
        SAMPLE_SIZE_BITS, 
        CHANNELS,
        SIGNED,
        BIG_ENDIAN
    );
    private static AtomicBoolean isRecording = new AtomicBoolean(false);
    private static String outputFileName;
    
    // 私有构造函数，防止实例化
    private VoipPcmRecorder() {}
    
    /**
     * 开始接收PCM数据并添加数据
     * 第一次调用时会初始化录音，后续调用会继续添加数据
     * 如果文件不存在则会创建新的录音文件
     * 
     * @param pcmData PCM数据字节数组
     * @param fileName 可选的文件名，仅在第一次调用时有效，如果为null则使用默认文件名
     * @return 是否成功接收数据
     */
    public static synchronized boolean receiveData(byte[] pcmData, String fileName) {
        // 如果是第一次调用，初始化录音
        if (!isRecording.get()) {
            // 设置文件名
            if (fileName != null && !fileName.trim().isEmpty()) {
                outputFileName = fileName;
                if (!outputFileName.toLowerCase().endsWith(".wav")) {
                    outputFileName += ".wav";
                }
            } else {
                // 生成默认文件名：voip_record_yyyyMMdd_HHmmss.wav
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                outputFileName = "voip_record_" + sdf.format(new Date()) + ".wav";
            }
            
            // 重置缓冲区
            pcmDataBuffer.reset();
            isRecording.set(true);
            
            System.out.println("开始接收8kHz VoIP PCM数据...");
            System.out.println("目标文件: " + outputFileName);
        }
        
        // 添加PCM数据
        if (pcmData == null || pcmData.length <= 0) {
            return false;
        }
        
        pcmDataBuffer.write(pcmData, 0, pcmData.length);
        return true;
    }
    
    /**
     * 开始接收PCM数据并添加数据（使用默认文件名）
     * 
     * @param pcmData PCM数据字节数组
     * @return 是否成功接收数据
     */
    public static boolean receiveData(byte[] pcmData) {
        return receiveData(pcmData, null);
    }
    
    /**
     * 停止接收并保存为WAV格式文件
     * 
     * @return 保存的文件路径，如果失败返回null
     */
    public static synchronized String stopAndSave() {
        if (!isRecording.get()) {
            System.out.println("当前未在接收状态");
            return null;
        }
        
        isRecording.set(false);
        
        try {
            byte[] pcmData = pcmDataBuffer.toByteArray();
            if (pcmData.length == 0) {
                System.out.println("未接收到任何PCM数据");
                return null;
            }
            
            System.out.println("停止接收PCM数据，共接收 " + pcmData.length + " 字节");
            System.out.println("正在保存为WAV格式...");
            
            // 创建输出文件
            File outputFile = new File(outputFileName);
            
            // 如果文件不存在，创建目录
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 创建音频输入流
            ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
            AudioInputStream audioInputStream = new AudioInputStream(bais, audioFormat, pcmData.length / audioFormat.getFrameSize());
            
            // 保存为WAV文件
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
            
            // 关闭流
            audioInputStream.close();
            bais.close();
            
            System.out.println("WAV文件保存成功: " + outputFile.getAbsolutePath());
            System.out.println("文件大小: " + outputFile.length() + " 字节");
            
            // 计算录音时长
            long durationMs = (pcmData.length / (audioFormat.getFrameSize() * (long)audioFormat.getSampleRate())) * 1000;
            System.out.println("录音时长: " + durationMs + " 毫秒");
            
            return outputFile.getAbsolutePath();
            
        } catch (IOException e) {
            System.err.println("保存WAV文件失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 获取当前接收状态
     * 
     * @return 是否正在接收数据
     */
    public static boolean isReceiving() {
        return isRecording.get();
    }
    
    /**
     * 获取已接收的数据大小（字节）
     * 
     * @return 数据大小
     */
    public static int getReceivedDataSize() {
        return pcmDataBuffer.size();
    }
    
    /**
     * 获取音频格式信息
     * 
     * @return 音频格式
     */
    public static AudioFormat getAudioFormat() {
        return audioFormat;
    }
    
    /**
     * 获取目标文件路径
     * 
     * @return 文件路径
     */
    public static String getOutputFileName() {
        return outputFileName;
    }
    
    // 测试方法
    public static void main(String[] args) {
        System.out.println("开始VoIP PCM录音测试...");
        
        // 模拟接收一些PCM数据（这里是空数据，实际使用时替换为真实的PCM数据）
        byte[] sampleData = new byte[1600]; // 100ms的8kHz 16bit单声道数据
        
        // 第一次调用，传入文件名
        VoipPcmRecorder.receiveData(sampleData, "test_voip_recording.wav");
        
        // 后续调用，只传入数据
        for (int i = 0; i < 49; i++) { // 模拟剩余4.9秒的数据
            VoipPcmRecorder.receiveData(sampleData);
            try {
                Thread.sleep(100); // 模拟100ms间隔
            } catch (InterruptedException e) {
                break;
            }
        }
        
        // 停止接收并保存
        String savedFile = VoipPcmRecorder.stopAndSave();
        if (savedFile != null) {
            System.out.println("测试完成，文件已保存: " + savedFile);
        }
    }
} 