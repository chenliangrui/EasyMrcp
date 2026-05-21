# mod_easymrcp_spy

FreeSWITCH模块，用于实时监听通话音频并转发到EasyMrcp服务器进行ASR识别。  
mod_easymrcp_spy本身是一个将音频流转发到指定ip和端口的模块。

## 功能特性

- 🎧 **实时音频监听**：使用MediaBug技术监听通话音频
- 🔄 **PCM到G.711A转换**：高性能音频编码转换
- 📡 **RTP音频流**：将音频数据封装为RTP包发送
- ⚡ **高性能**：优化的G.711A编码算法，适合实时处理

## 编译安装

### 1. 编译模块

```bash
cd /path/to/mod_easymrcp_spy
make
```

### 2. 安装到FreeSWITCH

```bash
sudo make install
```

### 3. 加载模块

在FreeSWITCH控制台执行：
```
load mod_easymrcp_spy
```

或在`modules.conf.xml`中添加：
```xml
<load module="mod_easymrcp_spy"/>
```

## 使用方法

### 启动音频监听

```
easymrcp_spy_start <target_ip>:<target_port>
```

**参数说明：**
- `target_ip`: EasyMrcp服务器IP地址
- `target_port`: EasyMrcp服务器RTP接收端口

**示例：**
```
easymrcp_spy_start 192.168.1.100:8000
```

### 停止音频监听

```
easymrcp_spy_stop
```

## 应用场景

### 1. 拨号计划中使用

```xml
<extension name="asr_call">
  <condition field="destination_number" expression="^(\d+)$">
    <action application="answer"/>
    <action application="easymrcp_spy_start" data="192.168.1.100:8000"/>
    <action application="bridge" data="user/$1"/>
    <action application="easymrcp_spy_stop"/>
  </condition>
</extension>
```

### 2. Python脚本中使用

```python
# 启动音频监听
session.execute("easymrcp_spy_start", "192.168.1.100:8000")

# 进行通话处理
# ...

# 停止音频监听
session.execute("easymrcp_spy_stop")
```

## 技术细节

- **音频格式**: 输入PCM 16位，输出G.711A编码
- **RTP封装**: 标准RTP包格式，Payload Type = 8
- **采样率**: 8KHz（电话音质）
- **帧长**: 支持任意长度的音频帧

## 日志级别

- **INFO**: 模块加载/卸载、启动/停止信息
- **DEBUG**: 详细的音频处理和RTP发送信息
- **ERROR**: 错误信息和异常情况

## 注意事项

1. **网络连通性**: 确保FreeSWITCH服务器能够访问EasyMrcp服务器
2. **端口占用**: 模块会自动分配本地RTP端口
3. **内存管理**: 模块会自动管理内存，无需手动干预
4. **并发支持**: 支持多个通话同时进行音频监听
5. **上下文管理**: 命令只能在某个电话的session中执行