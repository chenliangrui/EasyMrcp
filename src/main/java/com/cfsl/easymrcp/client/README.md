# Netty TCP 客户端

一个轻量级的基于Netty实现的TCP客户端，用于与EasyMrcp服务进行交互。

## 特点

- 使用Netty框架实现，性能高效
- 与服务端保持相同的消息协议和格式
- 支持与EasyMrcp服务的所有通信功能
- 简洁易用的API，便于集成到其他Java应用
- 可通过命令行交互方式使用

## 快速开始

### 启动客户端

#### 在Linux/Mac上:

```bash
./netty_tcp_client.sh [host] [port]
```

#### 在Windows上:

```cmd
netty_tcp_client.bat [host] [port]
```

默认连接localhost:9090

### 命令行使用

连接成功后，可使用以下命令:

1. `speak <message>` - 发送语音合成事件
2. `detect-speech` - 开始语音识别
3. `interrupt` - 打断当前TTS
4. `interrupt_speak <message>` - 打断当前TTS并播放新内容
5. `disconnect` - 断开连接
6. `exit` - 退出客户端

## 在其他Java应用中使用

```java
// 创建客户端
NettyTcpClient client = new NettyTcpClient("localhost", 9090);

// 连接服务器
if (client.connect()) {
    try {
        // 设置客户端ID
        client.setClientId("your-client-id");
        
        // 发送语音合成事件
        client.sendEvent(TcpEventType.Speak, "你好，这是一条语音合成消息");
        
        // 发送语音识别事件
        JSONObject detectSpeechParams = new JSONObject();
        detectSpeechParams.put(ASRConstant.StartInputTimers, true);
        detectSpeechParams.put(ASRConstant.NoInputTimeout, 60000);
        detectSpeechParams.put(ASRConstant.SpeechCompleteTimeout, 800);
        client.sendEvent(TcpEventType.DetectSpeech, detectSpeechParams.toJSONString());
        
        // 使用完毕后关闭连接
        client.close();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

## 消息格式

客户端和服务端使用相同的消息格式:

```
+-------------------+------------------------+
| 消息头(8字节)      | 消息体(变长)           |
+-------------------+------------------------+
| 魔数(4字节) | 长度(4字节) | JSON数据        |
+-------------------+------------------------+
```

魔数固定为 0x66AABB99，用于标识消息的开始。

## 事件类型

客户端支持以下事件类型:

- `Speak` - 语音合成
- `DetectSpeech` - 语音识别
- `SpeakInterrupted` - 打断语音合成
- `InterruptAndSpeak` - 打断并开始新的语音合成
- `ClientConnect` - 客户端连接
- `ClientDisConnect` - 客户端断开 