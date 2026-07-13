# java-enhanced-client

一个独立的 Java 示例工程，用于演示如何通过 `EnhancedNettyTcpClient` 接入 EasyMrcp 的自定义 TCP 协议。

## 包含内容

- 回调式 Netty TCP 客户端
- EasyMrcp 协议事件模型
- `DetectSpeech` / `Speak` 示例流程
- 独立 Maven 构建

## 协议格式

客户端和服务端都使用 8 字节头 + JSON 消息体：

```text
+-------------------+------------------------+
| 消息头(8字节)      | 消息体(变长)           |
+-------------------+------------------------+
| 魔数(4字节) | 长度(4字节) | JSON数据        |
+-------------------+------------------------+
```

魔数固定为 `0x66AABB99`。

## 编译

```bash
mvn -f examples/java-enhanced-client/pom.xml clean compile
```

## 运行

```bash
mvn -f examples/java-enhanced-client/pom.xml exec:java -Dexec.mainClass=com.cfsl.easymrcp.examples.client.EnhancedNettyTcpClientExample
```

## 与主工程 `NettyTcpClient` 的区别

- 当前示例以回调驱动方式处理事件
- 当前示例支持 `eventId`
- 当前示例更贴近业务集成而不是命令行交互

## 启动参数

可选参数：

1. `host`，默认 `127.0.0.1`
2. `port`，默认 `9090`
3. `clientId`，默认随机 UUID

示例：

```bash
mvn -f examples/java-enhanced-client/pom.xml exec:java \
  -Dexec.mainClass=com.cfsl.easymrcp.examples.client.EnhancedNettyTcpClientExample \
  -Dexec.args="127.0.0.1 9090 demo-client-001"
```
