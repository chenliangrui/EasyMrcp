# examples/scripts

这里存放 EasyMrcp 的 FreeSWITCH 演示脚本，供示例联调和快速体验使用。

## 文件说明

- `mrcp_handler.py`
  - FreeSWITCH 拨号计划调用的示例脚本。
  - 负责桥接通话、透传 `X-EasyMRCP`，并演示如何通过 TCP client 调用 EasyMrcp 的 `Speak`、`DetectSpeech` 等事件。
- `tcp_client.py`
  - EasyMrcp 自定义 TCP 协议的 Python 客户端示例封装。
  - 可被 `mrcp_handler.py` 直接调用，也可以作为其他 FreeSWITCH Python 脚本的参考实现。

## 使用前提

- FreeSWITCH 需要支持 Python 脚本执行。
- FreeSWITCH 所使用的 Python 环境需要安装 `gevent` 等依赖。
- EasyMrcp 服务端地址需要与脚本中的硬编码配置一致。

## 脚本配置

### 1. 复制脚本

将 `mrcp_handler.py` 和 `tcp_client.py` 复制到 FreeSWITCH 的脚本目录，例如 `/usr/local/freeswitch/scripts/`。

### 2. 配置拨号计划

在 `/usr/local/freeswitch/conf/dialplan/default.xml` 中新增如下配置，让来电执行 `mrcp_handler`：

```plain
<?xml version="1.0" encoding="utf-8"?>
<include>
  <context name="default">
    <!-- 新增拨号计划，让所有号码都执行mrcp_handler脚本 -->
    <extension>
      <condition field="destination_number" expression="^\d+$">
        <action application="python" data="mrcp_handler"/>
      </condition>
    </extension>

  </context>
</include>
```

### 3. 修改 EasyMrcp 服务地址

在 `mrcp_handler.py` 中修改硬编码的 EasyMrcp 服务端地址：

```plain
    # 硬编码MRCP服务器信息
    server_host = "172.16.2.155"
    server_port = 9090
```

请将 `server_host` 和 `server_port` 改成你自己的 EasyMrcp 服务地址。

## 说明

- 这些脚本属于演示示例，不会作为应用运行时 resources 打包进最终 Jar。
- 完整的项目运行配置、ASR/TTS 配置和脚本接入示例，请结合仓库根目录 `README.md` 一起查看。
