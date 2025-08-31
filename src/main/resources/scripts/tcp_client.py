#!/usr/bin/env python
# -*- coding: utf-8 -*-

import socket
import json
import threading
import time
import sys
import struct
import freeswitch

# 设置默认编码为utf-8（Python 2特有）
try:
    reload(sys)  # Python 2
    sys.setdefaultencoding('utf-8')
except (NameError, AttributeError):
    pass  # Python 3不需要此操作

# 魔数，用于标识消息的开始，固定为 0x66AABB99
MAGIC_NUMBER = 0x66AABB99

# 消息头长度为8字节：4字节魔数 + 4字节消息体长度
HEADER_LENGTH = 8

# 安全日志函数，确保Python 2兼容性
def safe_log(level, message):
    """安全地记录日志，处理Python 2编码兼容问题"""
    if isinstance(message, unicode):
        message = message.encode('utf-8')
    freeswitch.consoleLog(level, message + "\n")

class MrcpEventType:
    """EasyMrcp事件类型枚举"""
    # 客户端发送的命令
    ClientConnect = "ClientConnect"
    Speak = "Speak"
    SpeakWithNoInterrupt = "SpeakWithNoInterrupt"
    DetectSpeech = "DetectSpeech"
    Interrupt = "Interrupt"
    InterruptAndSpeak = "InterruptAndSpeak"
    Silence = "Silence"
    # 客户端断开连接事件
    ClientDisConnect = "ClientDisConnect"
    
    # 服务器发送的事件
    ClientConnect = "ClientConnect"
    RecognitionComplete = "RecognitionComplete"
    SpeakComplete = "SpeakComplete"
    SpeakInterrupted = "SpeakInterrupted"
    NoInputTimeout = "NoInputTimeout"

class MrcpEvent:
    """EasyMrcp事件类"""
    def __init__(self, client_id, event_type, data=None):
        self.id = client_id
        self.event = event_type
        
        # 确保data是字符串类型
        if data is not None:
            if isinstance(data, dict) or isinstance(data, list):
                self.data = json.dumps(data, ensure_ascii=False)
            elif sys.version_info[0] < 3 and isinstance(data, unicode):  # Python 2
                self.data = data.encode('utf-8')
            else:
                self.data = str(data)
        else:
            self.data = data
    
    def to_json(self):
        """转换为JSON字符串"""
        event_dict = {
            "id": self.id,
            "event": self.event,
            "data": self.data
        }
        return json.dumps(event_dict, ensure_ascii=False)

class TcpMessagePacket:
    """TCP消息包装器，处理消息的打包和解包
    
    消息格式：
    +-------------------+------------------------+
    | 消息头(8字节)      | 消息体(变长)           |
    +-------------------+------------------------+
    | 魔数(4字节) | 长度(4字节) | JSON数据        |
    +-------------------+------------------------+
    """
    def __init__(self, message):
        self.message = message
    
    def pack(self):
        """打包消息：魔数(4字节) + 长度(4字节) + 内容"""
        # 确保message是utf-8编码的字符串
        message_bytes = None
        if sys.version_info[0] < 3:  # Python 2
            if isinstance(self.message, unicode):
                message_bytes = self.message.encode('utf-8')
            else:
                # 对于Python 2，先尝试解码为unicode再编码为utf-8
                try:
                    message_bytes = self.message.encode('utf-8')
                except UnicodeDecodeError:
                    message_bytes = self.message
        else:  # Python 3
            if isinstance(self.message, str):
                message_bytes = self.message.encode('utf-8')
            else:
                message_bytes = str(self.message).encode('utf-8')
            
        length = len(message_bytes)
        
        # 创建一个包含头部和消息体的缓冲区
        buffer = bytearray(HEADER_LENGTH + length)
        
        # 写入魔数 (0x66AABB99)
        buffer[0:4] = struct.pack("!I", MAGIC_NUMBER)
        
        # 写入消息体长度
        buffer[4:8] = struct.pack("!I", length)
        
        # 写入消息体
        buffer[8:] = message_bytes
        
        return buffer

class TcpMessageReader:
    """TCP消息读取器，用于从流中读取完整消息"""
    def __init__(self, sock):
        self.sock = sock
        self.buffer = b''
    
    def read_messages(self):
        """读取可能的多条消息"""
        messages = []
        
        # 尝试接收数据
        try:
            data = self.sock.recv(4096)
            if not data:  # 连接已关闭
                return messages
            
            self.buffer += data
            
            # 处理缓冲区中的所有完整消息
            while len(self.buffer) >= HEADER_LENGTH:  # 至少包含完整头部
                # 验证魔数
                magic = struct.unpack("!I", self.buffer[0:4])[0]
                if magic != MAGIC_NUMBER:
                    safe_log("WARNING", "警告: 魔数不匹配 (收到: %s, 期望: %s)" % (hex(magic), hex(MAGIC_NUMBER)))
                    # 尝试查找下一个可能的魔数位置
                    next_pos = self.find_next_magic_number()
                    if next_pos == -1:
                        # 没找到魔数，清空缓冲区
                        self.buffer = b''
                    else:
                        # 从下一个魔数位置开始处理
                        self.buffer = self.buffer[next_pos:]
                    continue
                
                # 解析消息长度
                length = struct.unpack("!I", self.buffer[4:8])[0]
                
                # 检查是否有完整消息
                total_length = HEADER_LENGTH + length
                if len(self.buffer) >= total_length:
                    # 提取消息内容，使用utf-8解码
                    try:
                        message = self.buffer[8:total_length].decode('utf-8')
                    except UnicodeDecodeError:
                        # 如果utf-8解码失败，尝试使用latin-1（不会失败）
                        message = self.buffer[8:total_length].decode('latin-1')
                    messages.append(message)
                    
                    # 更新缓冲区，移除已处理的消息
                    self.buffer = self.buffer[total_length:]
                else:
                    # 消息不完整，等待更多数据
                    break
        except socket.error as e:
            safe_log("ERR", "读取消息错误: %s" % str(e))
        
        return messages
    
    def find_next_magic_number(self):
        """查找缓冲区中下一个魔数的位置"""
        # 魔数是4字节，所以至少需要4字节才能查找
        if len(self.buffer) < 4:
            return -1
        
        # 从第2个字节开始查找
        for i in range(1, len(self.buffer) - 3):
            # 检查是否匹配魔数
            if (self.buffer[i] & 0xFF) == 0x66 and \
               (self.buffer[i+1] & 0xFF) == 0xAA and \
               (self.buffer[i+2] & 0xFF) == 0xBB and \
               (self.buffer[i+3] & 0xFF) == 0x99:
                return i
        
        return -1

class EasyMrcpTcpClient:
    """TCP客户端，用于与EasyMrcp服务器通信"""
    
    def __init__(self, server_host, server_port, client_id=None):
        self.server_host = server_host
        self.server_port = server_port
        self.socket = None
        self.message_reader = None
        self.connected = False
        self.client_id = client_id
        
        # 初始化回调字典
        self.event_callbacks = {}
        self.response_callback = None
    
    # 注册事件回调
    def register_event_callback(self, event_type, callback_func):
        """
        注册特定事件的回调函数
        
        Args:
            event_type: 事件类型，使用MrcpEventType中的常量
            callback_func: 回调函数，接受一个参数(data)
        """
        self.event_callbacks[event_type] = callback_func
        return self  # 支持链式调用
    
    # 注册响应回调
    def register_response_callback(self, callback_func):
        """
        注册响应消息的回调函数
        
        Args:
            callback_func: 回调函数，接受三个参数(code, message, data)
        """
        self.response_callback = callback_func
        return self  # 支持链式调用
    
    def connect(self):
        self.connect(None)

    def connect(self, data):
        """连接到服务器"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.server_host, self.server_port))
            self.message_reader = TcpMessageReader(self.socket)
            self.connected = True
            safe_log("INFO", "已连接到服务器 %s:%d" % (self.server_host, self.server_port))

            # 启动接收线程
            receive_thread = threading.Thread(target=self.receive_messages)
            receive_thread.daemon = True
            receive_thread.start()

            # 发送注册事件
            self.send_event(MrcpEventType.ClientConnect, data)

            return True
        except socket.error as e:
            safe_log("ERR", "连接EasyMrcp服务器失败: %s. address: %s:%d" % (str(e), self.server_host, self.server_port))
            return False
    
    def close(self):
        """关闭连接"""
        self.connected = False
        if self.socket:
            try:
                self.socket.close()
            except:
                pass
            self.socket = None
    
    def disconnect(self):
        """发送断开连接请求并关闭连接"""
        if self.connected:
            try:
                safe_log("INFO", "发送断开连接请求...")
                self.send_event(MrcpEventType.ClientDisConnect, None)
                # 给服务器一点时间处理断开请求
                time.sleep(0.5)
            except:
                pass
            finally:
                self.close()
                safe_log("INFO", "已断开与服务器的tcp连接")
    
    def send_event(self, event_type, data):
        """发送事件"""
        if not self.connected or not self.socket:
            safe_log("ERR", "未连接到服务器")
            return
        
        try:
            # 创建事件对象
            event = MrcpEvent(self.client_id, event_type, data)
            
            # 转换为JSON
            json_event = event.to_json()
            
            # 打包消息并发送
            packet = TcpMessagePacket(json_event)
            packed_data = packet.pack()
            self.socket.sendall(packed_data)
            
            safe_log("INFO", "发送事件: %s" % json_event)
        except Exception as e:
            safe_log("ERR", "发送事件异常: %s" % str(e))
            import traceback
            error_msg = traceback.format_exc()
            safe_log("ERR", error_msg)
    
    def receive_messages(self):
        """接收服务器消息"""
        try:
            while self.connected and self.message_reader:
                # 读取完整消息
                messages = self.message_reader.read_messages()
                
                # 处理每个消息
                for message in messages:
                    try:
                        # safe_log("INFO", "收到服务器响应")
                        
                        # 处理空响应或"null"响应
                        if not message or message.strip().lower() == "null":
                            safe_log("INFO", "收到空响应")
                            continue
                        
                        # 解析JSON
                        try:
                            json_obj = json.loads(message)
                            if not json_obj:  # 如果解析后仍为空
                                continue
                            
                            # 检查消息类型
                            if "event" in json_obj:
                                # 事件消息
                                event_name = json_obj.get("event")
                                data = json_obj.get("data")
                                self.handle_event(event_name, data)
                            elif "code" in json_obj:
                                # 响应消息
                                code = json_obj.get("code")
                                msg = json_obj.get("message")
                                data = json_obj.get("data")
                                self.handle_response(code, msg, data)
                        except ValueError:
                            # JSON解析失败，可能是二进制数据
                            safe_log("WARNING", "收到非JSON格式数据")
                    
                    except Exception as e:
                        safe_log("ERR", "处理响应异常: %s" % str(e))
                        import traceback
                        error_msg = traceback.format_exc()
                        safe_log("ERR", error_msg)

        except Exception as e:
            if self.connected:
                safe_log("ERR", "接收消息异常: %s" % str(e))
                import traceback
                error_msg = traceback.format_exc()
                safe_log("ERR", error_msg)
    
    def handle_event(self, event_name, data):
        """处理事件消息"""
        # 如果注册了该事件的回调，则调用回调函数
        if event_name in self.event_callbacks:
            try:
                self.event_callbacks[event_name](data)
            except Exception as e:
                safe_log("ERR", "调用事件回调异常: %s, %s" % (event_name, str(e)))
        else:
            safe_log("INFO", "收到未知事件: %s, 数据: %s" % (event_name, data))
    
    def handle_response(self, code, message, data):
        """处理响应消息"""
        # 如果注册了响应回调，则调用回调函数
        if self.response_callback:
            try:
                self.response_callback(code, message, data)
            except Exception as e:
                safe_log("ERR", "调用响应回调异常: %s" % str(e))
        # else:
        #     # 默认处理
        #     if code == 200:
        #         safe_log("INFO", "请求成功: %s" % message)
        #         if data:
        #             safe_log("INFO", "响应数据: %s" % data)
        #     else:
        #         safe_log("ERR", "请求失败 [%d]: %s" % (code, message))