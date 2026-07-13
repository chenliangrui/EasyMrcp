# 新增 ASR 厂商接入 EasyMrcp 开发说明

## 适用对象

这份文档面向第一次接触 EasyMrcp、需要把一家新的 ASR 厂商接入现有项目的业务开发者。

文档目标不是介绍 ASR 理论，而是告诉你：

- 应该先照哪套现有模板改
- 新增一家 ASR 时必须改哪些文件
- `dictation` 和 `transliterate` 两种模式分别怎么接
- 哪些地方最容易踩坑

## 最快接入路径

如果你只是想把一家新厂商接进来，先不要看原理，直接按讯飞模板改最快。

推荐顺序：

1. 先选一个最接近的模板，优先看 `src/main/java/com/cfsl/easymrcp/asr/xfyun/`。
2. 复制 `XfyunAsrConfig`、`XfyunDictationAsrProcessor` 或 `XfyunTransliterateAsrProcessor` 的结构。
3. 把厂商配置、连接地址、鉴权参数和结果回调改成新厂商的。
4. 在 `ProcessorCreator` 里加一个新的 `asrMode` 分支。
5. 在 `application.yaml` 里切换到新模式。
6. 用 `examples/java-enhanced-client` 或 `examples/scripts` 联调。

## 你需要改哪些文件

新增一家 ASR 厂商，通常至少会涉及下面这些文件。

| 文件 | 是否必须 | 作用 |
| --- | --- | --- |
| `pom.xml` | 视情况 | 增加厂商 SDK 依赖 |
| `src/main/java/com/cfsl/easymrcp/common/EMConstant.java` | 是 | 增加新的 `asrMode` 常量 |
| `src/main/java/com/cfsl/easymrcp/common/ProcessorCreator.java` | 是 | 注册新的 ASR 处理器创建逻辑 |
| `src/main/java/com/cfsl/easymrcp/asr/<vendor>/...` | 是 | 新增厂商配置类、处理器、SDK 封装 |
| `src/main/resources/asr/<vendor>-asr.properties` | 是 | 提供配置模板 |
| `src/main/resources/application.yaml` | 可选 | 改默认 `mrcp.asrMode` 做本地联调 |

推荐目录结构如下：

```text
src/main/java/com/cfsl/easymrcp/asr/
└─ 你的厂商目录/
   ├─ 你的 AsrConfig.java
   ├─ 你的 DictationProcessor.java
   ├─ 你的 TransliterateProcessor.java
   └─ 你的厂商客户端.java

src/main/resources/asr/
└─ 你的厂商配置.properties
```

如果你是第一次接新厂商，建议直接先对照讯飞目录看一遍。讯飞是当前项目里最完整的一套 ASR 接法，`dictation` 和 `transliterate` 两种模式都有：

```text
src/main/java/com/cfsl/easymrcp/asr/xfyun/
├─ XfyunAsrConfig.java
├─ dictation/
│  ├─ XfyunDictationAsrProcessor.java
│  └─ XfyunDictationWsClient.java
└─ transliterate/
   ├─ XfyunTransliterateAsrProcessor.java
   └─ XfyunTransliterateWsClient.java
```

## 接入步骤

### 第 1 步：先复制讯飞模板

先把最接近的讯飞实现复制出来，再改成新厂商的名字和参数。

如果你的厂商更像“一句话识别”，优先参考：

- `src/main/java/com/cfsl/easymrcp/asr/xfyun/dictation/XfyunDictationAsrProcessor.java`
- `src/main/java/com/cfsl/easymrcp/asr/xfyun/dictation/XfyunDictationWsClient.java`

如果你的厂商更像“长连接实时转写”，优先参考：

- `src/main/java/com/cfsl/easymrcp/asr/xfyun/transliterate/XfyunTransliterateAsrProcessor.java`
- `src/main/java/com/cfsl/easymrcp/asr/xfyun/transliterate/XfyunTransliterateWsClient.java`

这里先不用深究原理，只要先选对模板就行。`dictation` / `transliterate` 的区别放在文末补充说明里。

### 第 2 步：补齐模式和配置

这一组动作可以一起做：

1. 如果厂商有 Java SDK，在 `pom.xml` 增加依赖。
2. 在 `EMConstant` 增加新的 `asrMode` 常量。这里不要自己发明写法，直接照着现有讯飞常量的形式加一条：

```java
public static final String XFYUN = "xfyun";
```

新增厂商时，你只需要照着这一行的形式，再补一条自己的常量。

3. 新增配置类。这里先看讯飞的真实写法：

```java
@ConfigurationProperties(prefix = "xfyun-asr")
@PropertySource(
        // 先读类路径下的默认模板，再允许部署目录覆盖
        value = {"classpath:asr/xfyun-asr.properties", "file:asr/xfyun-asr.properties"},
        ignoreResourceNotFound = true
)
public class XfyunAsrConfig extends AsrConfig {
    // 厂商接入地址
    public String hostUrl;
    // 厂商鉴权参数
    public String APPID;
    public String APISecret;
    public String APIKey;
}
```

新增厂商时，配置类就照着这段改，主要只改 4 个地方：

1. `prefix`，把 `xfyun-asr` 改成你的新前缀
2. `@PropertySource` 里的文件名
3. 鉴权字段名
4. 如果厂商还有额外参数，就继续往这个配置类里加

4. 配置文件也直接先参考 `src/main/resources/asr/xfyun-asr.properties`。文档里只保留讯飞这份真实结构：

```properties
####################################语音听写(流式版)####################################
# 地址与鉴权信息
xfyun-asr.host-url=https://iat-api.xfyun.cn/v2/iat
xfyun-asr.APPID=
xfyun-asr.APISecret=
xfyun-asr.APIKey=
# 必填：dictation(一句话识别) 或 transliterate(长连接转写)
xfyun-asr.identify-patterns=dictation

####################################实时语音转写####################################
# xfyun-asr.host-url=rtasr.xfyun.cn/v1/ws
# xfyun-asr.APPID=
# xfyun-asr.APIKey=
# 如果厂商只支持 16k PCM，需要打开重采样
# xfyun-asr.re-sample=upsample8kTo16k
# xfyun-asr.identify-patterns=transliterate
```

新增厂商时，不要照着上面“改键值”，而是照着这个文件结构新建一份你自己的 `asr/*.properties`：

- 保留 `identify-patterns`
- 保留 `re-sample`
- 把讯飞的键名换成你自己的厂商键名

这里要记住一个项目约束：`src/main/resources/asr/**` 不会被打进最终 Jar，所以部署时要在运行目录额外提供一份对应的 `asr/*.properties`。

### 第 3 步：实现厂商处理器

厂商处理器统一继承 `AsrHandler`，实现这 4 个方法：

- `create()`
- `receive(byte[] pcmData)`
- `sendEof()`
- `asrClose()`

这里先看讯飞 `dictation` 的真实结构。`XfyunDictationAsrProcessor` 并不直接解析识别结果，它只做 4 件事：

下面这段是从现有实现里抽出来的关键结构，不是可直接复制编译的完整类：

```java
public class XfyunDictationAsrProcessor extends AsrHandler {
    // 从配置类里取出的厂商连接参数
    private String hostUrl;
    private String appid;
    private String apiSecret;
    private String apiKey;
    // 厂商协议适配层
    XfyunDictationWsClient xfyunWsClient;
    // 当前通话对应的 WebSocket 和 HTTP 客户端资源
    WebSocket webSocket;
    private OkHttpClient client;

    @Override
    public void create() {
        // create() 主要负责准备厂商连接，并把 EasyMrcp 的回调链路接到厂商客户端上
        String authUrl = XfyunDictationWsClient.getAuthUrl(hostUrl, apiKey, apiSecret);
        client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(url).build();
        xfyunCallback = (action, msg) -> getCallback().apply(action, msg);
        xfyunWsClient = new XfyunDictationWsClient(
                xfyunCallback, stop, getCountDownLatch(), getInterruptEnable(), getCallId(), getPushAsrRealtimeResult());
        webSocket = client.newWebSocket(request, xfyunWsClient);
    }

    @Override
    public void receive(byte[] pcmData) {
        // EasyMrcp 已经把 RTP 解码成 PCM，这里直接转发给讯飞
        xfyunWsClient.sendBuffer(pcmData);
    }

    @Override
    public void sendEof() {
        // 一句话识别结束后，通知厂商封句
        xfyunWsClient.sendEof();
    }

    @Override
    public void asrClose() {
        // 通话结束后释放 WebSocket 连接
        webSocket.close(1000, "Normal closure");
        // 释放 OkHttp 线程池和连接池
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
```

这段代码对应的伪代码结构是：

```text
XfyunDictationAsrProcessor
├─ 从 XfyunAsrConfig 取 hostUrl / appid / key / secret
├─ create()
│  └─ 建立厂商连接，并把当前通话的回调和上下文挂进去
├─ receive(pcm)
│  └─ 把 PCM 继续交给厂商客户端
├─ sendEof()
│  └─ 告诉厂商“一句话结束了”
└─ asrClose()
   └─ 释放 WebSocket 和 HTTP 资源
```

真正处理“连接成功 / 中间结果 / 最终结果”的逻辑，在 `XfyunDictationWsClient` 里。这个类可以理解成“厂商协议适配层”：

```text
XfyunDictationWsClient
├─ onOpen()
│  └─ countDownLatch.countDown()
├─ onMessage()
│  ├─ 解析厂商返回包
│  ├─ 中间结果 -> 视情况触发 Interrupt
│  ├─ 中间结果 -> 视情况推送 AsrRealTimeResult
│  └─ 最终结果 -> 回调 ASRConstant.Result
└─ sendEof()
   └─ 发送厂商要求的结束帧
```

从讯飞源码里可以直接看到这几个关键点：

- `onOpen()` 里执行 `countDownLatch.countDown()`
- `onMessage()` 里解析结果
- 中间结果触发 `ASRConstant.Interrupt`
- 最终结果触发 `ASRConstant.Result`
- 实时推送走 `SipUtils.sendAsrRealTimeResultEvent(...)`

你新增厂商时，建议也按这个拆法来写：

```text
你的 Processor
├─ 管生命周期
├─ 管资源释放
└─ 把 PCM 转发给你的厂商客户端

你的厂商客户端
├─ 管厂商鉴权
├─ 管连接建立
├─ 管厂商响应包解析
└─ 把结果翻译成 Interrupt / Result / AsrRealTimeResult
```

如果你不想额外拆一个“厂商客户端”类，也可以把这些逻辑都写进 `Processor`，但后面会很快变乱，尤其是厂商返回包一复杂，排查问题会很痛苦。

新增厂商时，`XfyunDictationAsrProcessor` 这段代码你主要改的是：

1. 配置类类型：`XfyunAsrConfig`
2. 厂商客户端类型：`XfyunDictationWsClient`
3. 鉴权地址的生成方式
4. 最终 `receive()` / `sendEof()` / `asrClose()` 里调用的厂商方法

实现时只记住 4 个关键点：

1. `create()` 里一定要在厂商连接成功后执行 `countDownLatch.countDown()`。
2. `receive(byte[] pcmData)` 只负责把 PCM 喂给厂商，不要重复做 RTP 解包、解码、VAD 和重采样。
3. `dictation` 模式下，`sendEof()` 通常不能为空。
4. `asrClose()` 负责真正释放连接、线程池和客户端资源。

如果接的是 `transliterate`，整体写法相同，只是生命周期不同：

- `create()` 通常在通话开始时只建一次连接
- `receive(byte[] pcmData)` 持续送音频
- `sendEof()` 可以为空或只做句级刷新
- `asrClose()` 在通话结束时统一关闭连接

这一点在讯飞目录里也很清楚：

- `dictation/XfyunDictationAsrProcessor`：一句话识别，`sendEof()` 有实际逻辑
- `transliterate/XfyunTransliterateAsrProcessor`：长连接转写，连接建立和关闭方式不同

### 第 4 步：注册并联调

最后把实现挂到框架里：

1. 在 `ProcessorCreator` 注入新配置类。
2. 在 `createAsrHandler()` 中按 `identifyPatterns` 返回具体处理器。

这一段也建议直接参照讯飞已有分支。当前项目里讯飞就是按两种模式分别返回不同处理器：

```java
case EMConstant.XFYUN:
    if (ASRConstant.IDENTIFY_PATTERNS_DICTATION.equals(xfyunAsrConfig.getIdentifyPatterns())) {
        // 一句话识别 -> 讯飞听写处理器
        XfyunDictationAsrProcessor processor = new XfyunDictationAsrProcessor(xfyunAsrConfig);
        // 把 identifyPatterns / reSample 写回基类
        processor.setConfig(xfyunAsrConfig);
        return processor;
    } else if (ASRConstant.IDENTIFY_PATTERNS_TRANSLITERATE.equals(xfyunAsrConfig.getIdentifyPatterns())) {
        // 长连接转写 -> 讯飞转写处理器
        XfyunTransliterateAsrProcessor processor = new XfyunTransliterateAsrProcessor(xfyunAsrConfig);
        processor.setConfig(xfyunAsrConfig);
        return processor;
    }
```

伪代码结构如下：

```text
ProcessorCreator
└─ createAsrHandler()
   ├─ 读取 mrcp.asrMode
   ├─ 命中你新增的 mode
   ├─ 再看 identifyPatterns
   ├─ dictation -> new 你的Processor(config)
   └─ transliterate -> new 你的Processor(config)
```

3. 在 `application.yaml` 中切到新厂商：

```yaml
mrcp:
  # 当前讯飞示例是 xfyun，新增厂商时改成你自己的 mode 名称
  asrMode: xfyun
```

4. 用现有示例联调：

- `examples/java-enhanced-client`
- `examples/scripts`

最少要确认这几件事：

- 能创建 ASR 连接
- `receive(byte[] pcmData)` 持续收到音频
- 最终结果能触发 `RecognitionComplete`
- 开启实时推送时能收到 `AsrRealTimeResult`
- 开启自动打断时，`Interrupt` 能打断当前 TTS

## 补充说明

如果你只想照模板改代码，到这里就够了。下面这部分是补充说明，可以后面再看。

### EasyMrcp 里的 ASR 链路

新增厂商时，通常不需要改 SIP、RTP 和 VAD 主流程，只需要把厂商 SDK 正确挂进已有抽象里。

当前链路可以简化成：

1. SIP 建链后，`HandleSipInit.initAsr()` 初始化当前通话的 ASR 处理器。
2. `ProcessorCreator` 根据 `mrcp.asrMode` 选择具体厂商实现。
3. `AsrHandler` 负责把 RTP 音频接入厂商客户端。
4. 厂商返回识别结果后，通过 `AsrCallback` 回调给 EasyMrcp。
5. `DetectSpeechEventHandler` 再把结果转换成 `RecognitionComplete`、`AsrRealTimeResult` 和打断事件。

如果你想进一步读源码，可以重点看下面几个文件：

- `src/main/java/com/cfsl/easymrcp/asr/AsrHandler.java`
- `src/main/java/com/cfsl/easymrcp/rtp/NettyAsrRtpProcessor.java`
- `src/main/java/com/cfsl/easymrcp/common/ProcessorCreator.java`
- `src/main/java/com/cfsl/easymrcp/tcp/handler/DetectSpeechEventHandler.java`
- `src/main/java/com/cfsl/easymrcp/sip/handle/HandleSipInit.java`

### `dictation` 和 `transliterate` 的区别

EasyMrcp 里的 ASR 不只是“厂商不同”，还分两种接法：

#### `dictation`

一句话语音识别。

特点：

- 用户说一句，系统拿一句最终结果
- 通常需要显式发送 EOF 或 stop
- 适合参考 `xfyun/dictation`

#### `transliterate`

长连接实时转写。

特点：

- 整个通话期间通常维持一个长连接
- PCM 到来后持续送入厂商 SDK
- 可以实时推送中间结果
- 适合参考 `xfyun/transliterate`

如果一家厂商同时支持两种模式，建议拆成两个处理器类，不要在一个类里硬塞两套生命周期。
