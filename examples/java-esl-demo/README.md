# java-esl-demo

一个独立的 Spring Boot 示例工程，用于演示 FreeSWITCH ESL 事件如何与 EasyMrcp TCP 客户端串联。

## 覆盖范围

- 监听 ESL 呼叫事件
- 以通话 UUID 建立 EasyMrcp 客户端会话
- 在回调中发送 `DetectSpeech` / `Speak`
- 通话结束时释放会话资源

## 前置条件

在运行这个 demo 之前，需要先准备好下面几项：

- EasyMrcp 服务已经启动，并且能从当前机器访问
- FreeSWITCH 已启用 ESL 入站服务
- 你知道 FreeSWITCH 的 ESL 地址、端口、密码
- FreeSWITCH 的拨号计划会产生 `CHANNEL_PARK` / `CHANNEL_HANGUP` 事件

如果这些条件不满足，demo 虽然可能能编译，但不会真正接收到事件。

## 编译

```bash
mvn -f examples/java-esl-demo/pom.xml clean compile
```

## 运行

```bash
mvn -f examples/java-esl-demo/pom.xml spring-boot:run
```

## 配置说明

### 1. EasyMrcp 连接配置

下面这组配置控制 demo 连接哪一个 EasyMrcp 服务：

- `easy-mrcp.host`
  - EasyMrcp 服务地址
- `easy-mrcp.port`
  - EasyMrcp 自定义 TCP 控制端口
- `easy-mrcp.welcome-text`
  - 通话建立后首次播放的欢迎语
- `easy-mrcp.timeout-text`
  - 长时间没说话时回放的提示语

### 2. FreeSWITCH ESL 连接配置

这个 demo 使用 `freeswitch-esl-spring-boot-starter`，配置前缀必须是：

- `link.thingscloud.freeswitch.esl.inbound.*`

需要重点修改这些字段：

- `link.thingscloud.freeswitch.esl.inbound.default-password`
  - FreeSWITCH ESL 登录密码，通常默认是 `ClueCon`
- `link.thingscloud.freeswitch.esl.inbound.servers[0].host`
  - FreeSWITCH ESL 服务地址
- `link.thingscloud.freeswitch.esl.inbound.servers[0].port`
  - FreeSWITCH ESL 入站端口，通常默认是 `8021`
- `link.thingscloud.freeswitch.esl.inbound.servers[0].timeout-seconds`
  - ESL 建连超时，单位秒
- `link.thingscloud.freeswitch.esl.inbound.events`
  - 监听哪些事件；demo 直接用 `all`

### 3. 对应配置示例

```yaml
easy-mrcp:
  host: 127.0.0.1
  port: 9090
  welcome-text: 您好，请开始讲话。
  timeout-text: 您好，您还在线吗？

link:
  thingscloud:
    freeswitch:
      esl:
        inbound:
          default-password: ClueCon
          servers:
            - host: 127.0.0.1
              port: 8021
              timeout-seconds: 5
          events:
            - all
```

## 运行说明

当前版本在启动时会做一层最基本的校验：

- 如果 `InboundClient` bean 没有创建，应用会直接启动失败，而不是“假成功”退出。
- 如果 Spring Boot 正常启动并保持运行，说明 demo 已进入监听状态。

也就是说，看到 `Started EslEasyMrcpDemoApplication` 还不够，最好再观察是否有 ESL 相关连接日志，以及后续是否能收到事件。

## 事件流

1. FreeSWITCH 产生 `CHANNEL_PARK` 事件
2. `SimpleEslCallListener` 取出通话 UUID
3. 按 UUID 创建 `SimpleEslEasyMrcpHandler`
4. Handler 建立到 EasyMrcp 的 TCP 连接
5. `ClientConnect` 后发送欢迎语和 `DetectSpeech`
6. 用户说话后，收到 `RecognitionComplete`
7. demo 把识别结果回发成 `Speak`
8. FreeSWITCH 产生 `CHANNEL_HANGUP` 后关闭对应会话

## 当前示例的边界

- 不包含规则引擎
- 不包含 WebSocket 推送
- 不包含 Redis / CRM / 录音逻辑
- 仅演示 ESL 与 EasyMrcp 的最小接入链路
