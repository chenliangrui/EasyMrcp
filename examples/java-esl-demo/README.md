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
- `easy-mrcp.sip-user`
  - Java 创建 B-leg 时在 FreeSWITCH 中呼叫的 `user/<sip-user>` 目标

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
  - 监听哪些事件；demo 只订阅 `CHANNEL_PARK` 和 `CHANNEL_HANGUP`

### 3. 对应配置示例

```yaml
easy-mrcp:
  host: 127.0.0.1
  port: 9090
  sip-user: 1020

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
            - CHANNEL_PARK
            - CHANNEL_HANGUP
```

不要订阅 `all`：未被 demo 处理的 `HEARTBEAT`、`RE_SCHEDULE` 等系统事件会进入 ESL starter 的默认 handler，并输出无关的 WARN 日志。

## 运行说明

当前版本在启动时会做一层最基本的校验：

- 如果 `InboundClient` bean 没有创建，应用会直接启动失败，而不是“假成功”退出。
- 如果 Spring Boot 正常启动并保持运行，说明 demo 已进入监听状态。

也就是说，看到 `Started EslEasyMrcpDemoApplication` 还不够，最好再观察是否有 ESL 相关连接日志，以及后续是否能收到事件。

## FreeSWITCH 拨号计划

操作人员应在 FreeSWITCH 的 `conf/dialplan/default/` 目录下创建一个 include 文件（例如 `easymrcp.xml`；实际安装路径请按环境调整）。该目录会被包含到 `default` 拨号计划 context 中。保存后执行 `reloadxml` 使其生效。

Java demo 不会写入或重载 FreeSWITCH 的拨号计划或服务器配置；安装 include 文件和执行 `reloadxml` 都是操作人员的部署步骤。

下面是 include 文件的代表性内容：

```xml
<include>
  <extension name="java_dialplan">
    <condition field="destination_number" expression="^\d+$">
      <action application="park"/>
    </condition>
  </extension>
</include>
```

下列值取决于实际部署拓扑：

- `^\d+$` 表示所有数字号码均交给 Java 处理；请按实际业务范围收窄匹配规则。
- `user/<easy-mrcp.sip-user>` 是 Java demo 创建 B-leg 时使用的 EasyMrcp SIP 目标。配置的 SIP 用户必须与 EasyMrcp 的 `fs.register.username` 一致，并在呼叫前注册到 FreeSWITCH；FreeSWITCH 的 directory、domain 和注册路由必须使该目标可达。

目标 FreeSWITCH 必须启用并监听 Event Socket；其密码以及 ACL / 网络规则必须允许 Java demo 所在主机连接。该 FreeSWITCH 还必须能通过 Event Socket / API 暴露并支持 `uuid_answer`、`originate`、`uuid_bridge` 和 `uuid_kill`。

## 事件流

1. FreeSWITCH 产生 `CHANNEL_PARK` 事件
2. `SimpleEslCallListener` 取出事件中的 `Unique-ID`，并按该 UUID 建立 EasyMrcp TCP 客户端
3. demo 通过 ESL 执行 `uuid_answer <uuid>` 接听 A-leg
4. demo 执行 `originate {origination_uuid=<B-leg UUID>,easymrcp_bridge_leg=true,sip_h_X-EasyMRCP=<A-leg UUID>}user/<easy-mrcp.sip-user> &park()` 创建 B-leg
5. demo 执行 `uuid_bridge <A-leg UUID> <B-leg UUID>` 建立通话桥；B-leg 的 `CHANNEL_PARK` 因为带有标记而被忽略，避免递归建会话
6. EasyMrcp 的 SIP / RTP 会话初始化
7. `ClientConnect` 建立后发送 `Speak` / `DetectSpeech`
8. FreeSWITCH 产生 A-leg 的 `CHANNEL_HANGUP` 后关闭对应的客户端会话

## 当前示例的边界

- 不包含规则引擎
- 不包含 WebSocket 推送
- 不包含 Redis / CRM / 录音逻辑
- 仅演示 ESL 与 EasyMrcp 的最小接入链路
