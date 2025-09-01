# -*- coding: utf-8 -*-
"""
通话处理脚本 - A拨打B，同时为A和B启动RTP推送和ASR识别
整体思路是通过mod_easymrcp_spy模块将rtp流推送给EasyMrcp，进行asr识别并返回。
初始化时在connect中加入"Type": "spy"，发送连接事件并等待EayMrcp返回监听的rtp端口。
拿到端口后调用命令easymrcp_spy_start + EasyMrcp的ip:端口后即可推送rtp流
后续就是正常的开启EasyMrcp的asr流程。
"""

import freeswitch
import json
import time
import sys
import os
import threading

# 设置默认编码为utf-8（Python 2特有）
try:
    reload(sys)
    sys.setdefaultencoding('utf-8')
except (NameError, AttributeError):
    pass

# 导入EasyMrcp的TCP客户端
sys.path.append(os.path.dirname(os.path.realpath(__file__)))
from tcp_client import EasyMrcpTcpClient, MrcpEventType

# EasyMrcp服务器配置
MRCP_SERVER_HOST = "172.16.2.155"
MRCP_SERVER_PORT = 9090

# 安全日志函数
def safe_log(level, message):
    if isinstance(message, unicode):
        message = message.encode('utf-8')
    freeswitch.consoleLog(level, message + "\n")

# ASR处理线程
def asr_thread(uuid, leg_name, session_obj, rtp_target_ip, port_event, port_container):
    """为指定UUID的通话leg启动spy，获取端口后通知handler"""
    try:
        safe_log("INFO", "%s ASR线程启动，UUID: %s, MRCP服务器: %s:%s" %
                 (leg_name, uuid, MRCP_SERVER_HOST, MRCP_SERVER_PORT))

        # 创建TCP客户端
        client = EasyMrcpTcpClient(MRCP_SERVER_HOST, MRCP_SERVER_PORT, uuid)

        # 定义事件回调函数
        def on_client_connected(data):
            safe_log("INFO", "%s ASR连接成功，返回数据: %s" % (leg_name, data))
            
            # 从返回的数据中获取ASR服务器的RTP接收端口
            try:
                data_dict = json.loads(data)
                rtp_port = data_dict.get('rtpPort')
                if rtp_port:
                    # 保存端口信息到容器中
                    port_container[0] = rtp_port
                    safe_log("INFO", "%s 获取到ASR接收端口: %s" % (leg_name, rtp_port))
                    # 通知handler可以启动RTP推送了
                    port_event.set()
                else:
                    safe_log("ERR", "%s ASR返回数据中没有rtpPort字段" % leg_name)
                    port_event.set()  # 即使失败也要通知，避免死锁
                    return
            except Exception as e:
                safe_log("ERR", "%s 解析ASR返回数据失败: %s" % (leg_name, str(e)))
                port_event.set()  # 即使失败也要通知，避免死锁
                return
            
            # 开始语音识别
            safe_log("INFO", "%s 开始语音识别" % leg_name)
            detect_speech_params = {
                "StartInputTimers": False,
                "NoInputTimeout": 60000,
                "SpeechCompleteTimeout": 800,
                "AutomaticInterruption": True
            }
            client.send_event(MrcpEventType.DetectSpeech, json.dumps(detect_speech_params))

        def on_recognition_complete(data):
            safe_log("INFO", "%s 识别结果: %s" % (leg_name, data))

        def on_no_input_timeout(data):
            safe_log("INFO", "%s 语音识别超时" % leg_name)

        # 注册事件回调
        client.register_event_callback(MrcpEventType.ClientConnect, on_client_connected)
        client.register_event_callback(MrcpEventType.RecognitionComplete, on_recognition_complete)
        client.register_event_callback(MrcpEventType.NoInputTimeout, on_no_input_timeout)

        # 使用spy参数进行连接
        connect_params = {
            "Type": "spy"
        }
        client.connect(connect_params)
        
        # 跟随会话生命周期
        while session_obj.ready():
            time.sleep(0.5)
        
        client.disconnect()
        safe_log("INFO", "%s ASR线程结束" % leg_name)
        
    except Exception as e:
        safe_log("ERR", "%s ASR线程异常: %s" % (leg_name, str(e)))
        port_event.set()  # 确保即使出错也设置事件，避免死锁

def handler(session, args):
    """
    A拨打B，启动RTP推送和ASR识别
    """
    # RTP目标IP就是MRCP服务器IP
    rtp_target_ip = MRCP_SERVER_HOST
    
    # 获取A和B的信息
    caller = session.getVariable("caller_id_number")
    callee = session.getVariable("destination_number")
    a_uuid = session.getVariable("uuid")
    domain = session.getVariable("domain_name")
    
    safe_log("INFO", "通话开始: %s -> %s, A-UUID: %s, RTP目标: %s, MRCP服务器: %s:%s" % 
             (caller, callee, a_uuid, rtp_target_ip, MRCP_SERVER_HOST, MRCP_SERVER_PORT))
    
    # 应答A的通话
    session.answer()
    
    # 创建A-leg端口获取事件和容器
    a_port_event = threading.Event()
    a_port_container = [None]
    
    # 启动A-leg的ASR识别线程
    safe_log("INFO", "启动A-leg ASR连接")
    asr_thread_a = threading.Thread(target=asr_thread, args=(a_uuid, "A-leg", session, rtp_target_ip, a_port_event, a_port_container))
    asr_thread_a.daemon = True
    asr_thread_a.start()
    
    # 等待A-leg ASR获取到端口
    safe_log("INFO", "等待A-leg ASR获取端口...")
    if a_port_event.wait(timeout=10):  # 等待最多10秒
        if a_port_container[0]:
            # 启动A-leg RTP推送
            rtp_target = "%s:%s" % (rtp_target_ip, a_port_container[0])
            safe_log("INFO", "A-leg 启动RTP推送到: %s" % rtp_target)
            session.execute("easymrcp_spy_start", rtp_target)
        else:
            safe_log("ERR", "A-leg ASR未返回有效端口")
    else:
        safe_log("ERR", "A-leg ASR端口获取超时")
    
    # 创建B-leg session，设置正确的主叫信息
    caller_name = session.getVariable("caller_id_name") or caller
    destination = "{origination_caller_id_name='%s',origination_caller_id_number='%s'}user/%s" % (
        caller_name, caller, callee)
    safe_log("INFO", "创建B-leg session: %s" % destination)
    session2 = freeswitch.Session(destination)
    
    if session2.ready():
        b_uuid = session2.getVariable("uuid")
        safe_log("INFO", "B-leg UUID: %s" % b_uuid)
        
        # 创建B-leg端口获取事件和容器
        b_port_event = threading.Event()
        b_port_container = [None]
        
        # 启动B-leg的ASR识别线程
        safe_log("INFO", "启动B-leg ASR连接")
        asr_thread_b = threading.Thread(target=asr_thread, args=(b_uuid, "B-leg", session2, rtp_target_ip, b_port_event, b_port_container))
        asr_thread_b.daemon = True
        asr_thread_b.start()
        
        # 等待B-leg ASR获取到端口
        safe_log("INFO", "等待B-leg ASR获取端口...")
        if b_port_event.wait(timeout=10):  # 等待最多10秒
            if b_port_container[0]:
                # 启动B-leg RTP推送
                rtp_target = "%s:%s" % (rtp_target_ip, b_port_container[0])
                safe_log("INFO", "B-leg 启动RTP推送到: %s" % rtp_target)
                session2.execute("easymrcp_spy_start", rtp_target)
            else:
                safe_log("ERR", "B-leg ASR未返回有效端口")
        else:
            safe_log("ERR", "B-leg ASR端口获取超时")
        
        # 桥接两个session
        safe_log("INFO", "桥接A-leg和B-leg")
        freeswitch.bridge(session, session2)
        
        # 通话结束，停止B-leg RTP推送
        safe_log("INFO", "通话结束，停止B-leg RTP推送")
        session2.execute("easymrcp_spy_stop")
    else:
        safe_log("ERR", "B-leg session创建失败")
    
    # 通话结束，停止A-leg RTP推送
    safe_log("INFO", "通话结束，停止A-leg RTP推送")
    session.execute("easymrcp_spy_stop")
    
    safe_log("INFO", "通话处理完成: %s -> %s" % (caller, callee)) 