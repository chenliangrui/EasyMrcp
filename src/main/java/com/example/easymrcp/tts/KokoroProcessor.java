package com.example.easymrcp.tts;

import com.example.easymrcp.mrcp.Callback;
import com.example.easymrcp.rtp.RtpSender;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

public class KokoroProcessor extends TtsHandler {
    private static final String API_URL = "http://172.16.2.207:8880/v1/audio/speech";
    private HttpClient httpClient;
    //    private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
    RealTimeAudioProcessor processor;
    boolean stop = false;

    @Override
    public void transmit(String text) {
        String jsonPayload = String.format(
                "{\"model\":\"kokoro\",\"input\":\"%s\",\"voice\":\"zf_xiaoxiao\",\"response_format\":\"pcm\",\"stream\":true}",
                text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // 使用自定义的BodyHandler处理流式响应
        httpClient.sendAsync(request, new StreamBodyHandler())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        processAudioStream(response.body());
                    } else {
                        System.err.println("HTTP错误: " + response.statusCode());
                    }
                })
                .exceptionally(e -> {
                    System.err.println("请求异常: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                });
    }

    @Override
    public void stop() {
        stop = true;
    }

    // 自定义响应体处理器
    private static class StreamBodyHandler implements HttpResponse.BodyHandler<InputStream> {
        @Override
        public HttpResponse.BodySubscriber<InputStream> apply(HttpResponse.ResponseInfo responseInfo) {
            return HttpResponse.BodySubscribers.ofInputStream();
        }
    }

    /**
     * 发送pcm数据
     *
     * @param inputStream
     */
    private void processAudioStream(InputStream inputStream) {
        try {
//            processor.initAudioCapture();
            processor.startProcessing();
            processor.startRtpSender();
            byte[] resampledBuffer = new byte[1024 / 3 * 2]; // 调整缓冲区大小
//            RealTimePCMDownsampler processor = new RealTimePCMDownsampler();

            try (inputStream) {
                byte[] pcmBuffer = new byte[409600];
                int bytesRead;

                while ((bytesRead = inputStream.read(pcmBuffer)) != -1) {
//                    byte[] process = processor.process(pcmBuffer);
                    processor.putData(pcmBuffer, bytesRead);
//                    audioBuffer.write(pcmBuffer, 0, bytesRead);
                    // 实时重采样 24kHz -> 8kHz
//                    int resampledSize = resample24kTo8k(pcmBuffer, bytesRead, resampledBuffer);

                    // G.711u编码
//                    byte[] g711Data = g711Encoder.encode(resampledBuffer, resampledSize);
                }
            }
            //TODO mrcp报错是因为正常的打断，语音完成时机需要等rtp发送完成
            Thread.sleep(60000);
            if (!stop) {
                getCallback().apply(null);
            }
            // 执行降采样
//            byte[] pcm8k = downsample24kTo8k(audioBuffer.toByteArray());
//            playPCM(audioBuffer.toByteArray());
            // 分块处理并播放
//            RealtimeDownsamplerPlayer player = new RealtimeDownsamplerPlayer(rtpSender);
//            player.initAudio();
//            byte[] rawData = audioBuffer.toByteArray();
//            int chunkSize = 512;
//            for(int i=0; i<rawData.length; i+=chunkSize) {
//                int end = Math.min(i+chunkSize, rawData.length);
//                byte[] chunk = new byte[end-i];
//                System.arraycopy(rawData, i, chunk, 0, chunk.length);
//                player.processAndPlay(chunk);
//            }
//
//            player.audioLine.drain();
//            player.audioLine.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * PCM降采样核心算法
     *
     * @param input 输入字节流（24kHz 16bit单声道）
     * @return 8kHz采样率的字节流
     */
    public static byte[] downsample24kTo8k(byte[] input) {
        // 原始音频参数
        final int SOURCE_RATE = 24000;
        final int TARGET_RATE = 8000;
        final int SAMPLE_SIZE = 2; // 16bit=2字节
        final int CHANNELS = 1;

        // 计算采样率比
        final double RATIO = (double) SOURCE_RATE / TARGET_RATE;

        // 转换字节序为小端模式
        ByteBuffer buffer = ByteBuffer.wrap(input)
                .order(ByteOrder.LITTLE_ENDIAN);

        // 创建原始样本数组
        short[] sourceSamples = new short[input.length / SAMPLE_SIZE];
        for (int i = 0; i < sourceSamples.length; i++) {
            sourceSamples[i] = buffer.getShort();
        }

        // 计算目标样本数
        int targetLength = (int) (sourceSamples.length / RATIO);
        short[] targetSamples = new short[targetLength];

        // 抗混叠滤波参数
        final int FILTER_ORDER = 8;
        final double CUTOFF = 0.4; // 截止频率系数

        // 线性插值+滤波处理
        for (int i = 0; i < targetLength; i++) {
            double srcPos = i * RATIO;
            int basePos = (int) srcPos;
            double fraction = srcPos - basePos;

            // 边界检查
            if (basePos >= sourceSamples.length - 1) {
                targetSamples[i] = sourceSamples[sourceSamples.length - 1];
                continue;
            }

            // 线性插值
            short prev = sourceSamples[basePos];
            short next = sourceSamples[basePos + 1];
            double interpolated = prev * (1 - fraction) + next * fraction;

            // 简单移动平均滤波
            double sum = 0;
            int count = 0;
            for (int j = -FILTER_ORDER / 2; j <= FILTER_ORDER / 2; j++) {
                int pos = basePos + j;
                if (pos >= 0 && pos < sourceSamples.length) {
                    sum += sourceSamples[pos];
                    count++;
                }
            }
            double filtered = sum / count;

            // 混合插值和滤波结果
            targetSamples[i] = (short) ((interpolated + filtered * CUTOFF) / (1 + CUTOFF));
        }

        // 转换回字节流
        ByteBuffer outputBuffer = ByteBuffer.allocate(targetLength * SAMPLE_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : targetSamples) {
            outputBuffer.putShort(sample);
        }

        return outputBuffer.array();
    }

    // 新增重采样方法
    private int resample24kTo8k(byte[] input, int inputLength, byte[] output) {
        int sampleCount = inputLength / 2; // 16位样本数
        int outputIndex = 0;

        // 3:1降采样（24k->8k）
        for (int i = 0; i < sampleCount; i += 3) {
            // 取第一个样本（简单抽取法）
            System.arraycopy(input, i * 2, output, outputIndex, 2);
            outputIndex += 2;
        }
        return outputIndex;
    }

    // 播放PCM音频流
    public static void playPCM(byte[] pcmData) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(8000, 16, 1, true, false); // 16kHz,16位,单声道
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        InputStream input = new ByteArrayInputStream(pcmData);
        byte[] buffer = new byte[4096];
        try {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                line.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        line.drain();
        line.close();
    }

    @Override
    public void create(String ip, int port) {
        //初始化rtp
        try {
//            rtpSender = new RtpSender(InetAddress.getByName(ip).getHostName(), port);
            processor = new RealTimeAudioProcessor();
            processor.DEST_IP = ip;
            processor.DEST_PORT = port;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 创建kokoro的连接
        httpClient = HttpClient.newHttpClient();

    }

    @Override
    public void close() {

    }
}
