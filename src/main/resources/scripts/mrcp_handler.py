#!/usr/bin/env python
# -*- coding: utf-8 -*-

import freeswitch
import json
import time
import sys
import os
import threading

# 设置默认编码为utf-8（Python 2特有）
try:
    reload(sys)  # Python 2
    sys.setdefaultencoding('utf-8')
except (NameError, AttributeError):
    pass  # Python 3不需要此操作

# 导入EasyMrcp的TCP客户端
sys.path.append(os.path.dirname(os.path.realpath(__file__)))
from tcp_client import EasyMrcpTcpClient, MrcpEventType

# 安全日志函数，确保Python 2兼容性
def safe_log(level, message):
    """安全地记录日志，处理Python 2编码兼容问题"""
    if isinstance(message, unicode):
        message = message.encode('utf-8')
    freeswitch.consoleLog(level, message + "\n")

# 在单独线程中执行EasyMrcp事件的处理
def mrcp_in_thread(session, caller, callee):
    """在单独线程中执行EasyMrcp的事件处理，不阻塞主线程"""
    try:
        safe_log("INFO", "线程中执行EasyMrcp事件的处理")
        start_easymrcp_client(session)
        safe_log("INFO", "EasyMrcp处理线程执行完成")
    except Exception as e:
        safe_log("ERR", "处理线程异常: %s" % str(e))
        import traceback
        error_msg = traceback.format_exc()
        safe_log("ERR", error_msg)

# FreeSWITCH拨号计划直接调用的函数
def handler(session, args):
    """
    FreeSWITCH拨号计划直接调用的EasyMrcp事件的处理
    此处演示的是自定义sip header桥接EasyMrcp，一般情况下不需要修改
    
    Args:
        session: FreeSWITCH会话对象
        args: 参数字符串，可选，用于传递额外信息
    """
    safe_log("INFO", "=== EasyMrcp事件处理开始 ===")
    
    # 获取通话基本信息
    caller = session.getVariable("caller_id_number")
    callee = session.getVariable("destination_number")
    callId = session.getVariable("sip_call_id")
    chanName = session.getVariable("chan_name")
    
    safe_log("INFO", "通话callId = %s" % callId)
    safe_log("INFO", "通话chanName = %s" % chanName)
    safe_log("INFO", "主叫: %s, 被叫: %s" % (caller, callee))
    
    # 应答呼叫
    session.execute("answer", "")
    
    # 检查是否需要桥接到分机
    if callee and callee.isdigit() and len(callee) >= 3:
        # 获取A-leg的UUID
        a_leg_uuid = session.getVariable("uuid")
        
        # 创建MRCP处理线程
        mrcp_thread = threading.Thread(target=mrcp_in_thread, args=(session, caller, callee))
        mrcp_thread.daemon = True  # 设置为守护线程，主线程结束时自动结束
        mrcp_thread.start()
        
        safe_log("INFO", "EasyMrcp client处理线程已启动，继续执行桥接")

        # 只设置一个自定义SIP头，传递session的UUID
        session.setVariable("sip_h_X-EasyMRCP", a_leg_uuid)
        # 确保这个自定义头会被传递到B-leg
        session.setVariable("bridge_export", "sip_h_X-EasyMRCP")
        
        # 使用{}语法直接在bridge命令中设置变量
        bridge_command = "{origination_caller_id_name='%s',origination_caller_id_number='%s'}user/%s" % (
            session.getVariable("caller_id_name") or caller,
            caller,
            callee
        )
        safe_log("INFO", "桥接到分机: %s" % bridge_command)
        
        # 执行桥接
        session.execute("bridge", bridge_command)
        safe_log("INFO", "桥接已完成执行，A-leg UUID已通过X-EasyMRCP头传递")

def start_easymrcp_client(session):
    # 获取A-leg的UUID
    a_leg_uuid = session.getVariable("uuid")
    safe_log("INFO", "获取到通话UUID: %s" % a_leg_uuid)

    # 设置自定义SIP头，传递UUID
    session.setVariable("sip_h_X-EasyMRCP", a_leg_uuid)

    # 硬编码MRCP服务器信息
    server_host = "192.168.31.29"
    server_port = 9090

    safe_log("INFO", "连接EasyMRCP服务器 %s:%s" % (server_host, server_port))

    # 创建TCP客户端
    client = EasyMrcpTcpClient(server_host, int(server_port), a_leg_uuid)

    # 定义事件回调函数
    def on_client_connected(data):
        safe_log("INFO", "服务端sip模块连接成功，开始业务处理")

        # 发送欢迎语音
        welcome_msg = "您好，请您说一句话，我就会重复您的话哦~"
        client.send_event(MrcpEventType.Speak, welcome_msg)

        # 开始语音识别
        detect_speech_params = {
            "StartInputTimers": True,
            "NoInputTimeout": 60000,
            "SpeechCompleteTimeout": 800,
            "AutomaticInterruption": True
        }
        client.send_event(MrcpEventType.DetectSpeech, json.dumps(detect_speech_params))

    def on_recognition_complete(data):
        # 这里可以调用大模型或其他服务处理识别结果，注意当前回调函数是单线程，耗时操作可以另开线程进行处理，直接处理耗时操作可能影响tcp的事件接收哦~
        safe_log("INFO", "语音识别完成: %s" % data)
        client.send_event(MrcpEventType.Speak, data)

    def on_no_input_timeout(data):
        safe_log("INFO", "语音识别超时")
        client.send_event(MrcpEventType.Speak, "您好，您还可以听得到吗？")

    def on_speak_complete(data):
        safe_log("INFO", "语音合成播放完成")

    def on_speak_interrupted(data):
        safe_log("INFO", "语音合成被打断")

    # 注册事件回调
    client.register_event_callback(MrcpEventType.ClientConnect, on_client_connected)
    client.register_event_callback(MrcpEventType.RecognitionComplete, on_recognition_complete)
    client.register_event_callback(MrcpEventType.NoInputTimeout, on_no_input_timeout)
    client.register_event_callback(MrcpEventType.SpeakComplete, on_speak_complete)
    client.register_event_callback(MrcpEventType.SpeakInterrupted, on_speak_interrupted)
    client.connect()
    # 简单的会话监控循环
    try:
        # 等待会话结束
        while session.ready():
            time.sleep(0.5)
        # 断开连接
        client.disconnect()
        safe_log("INFO", "通话结束，已断开与EasyMrcp连接")
    except Exception as e:
        safe_log("ERR", "会话监控异常: %s" % str(e))