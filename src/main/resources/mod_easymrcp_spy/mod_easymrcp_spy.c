/*
 * FreeSWITCH Modular Media Switching Software Library / Soft-Switch Application
 * Copyright (C) 2005/2012, Anthony Minessale II <anthm@freeswitch.org>
 *
 * mod_easymrcp_spy.c -- EasyMrcp Spy Module
 *
 */
#include <switch.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdlib.h>



/* RTP头结构 - 使用字节方式避免位域问题 */
typedef struct {
    uint8_t vpxcc;      /* V(2), P(1), X(1), CC(4) */
    uint8_t mpt;        /* M(1), PT(7) */
    uint16_t sequence_number;
    uint32_t timestamp;
    uint32_t ssrc;
} rtp_header_t;

/* 模块数据结构 */
typedef struct {
    int sock_fd;
    struct sockaddr_in dest_addr;
    uint16_t seq_num;
    uint32_t timestamp;
    uint32_t ssrc;
    uint16_t rtp_port;
    uint16_t dest_port;
    char uuid[40];
    char target_ip[64];
} rtp_info_t;

/* 模块函数声明 */
SWITCH_MODULE_LOAD_FUNCTION(mod_easymrcp_spy_load);
SWITCH_MODULE_SHUTDOWN_FUNCTION(mod_easymrcp_spy_shutdown);
SWITCH_MODULE_DEFINITION(mod_easymrcp_spy, mod_easymrcp_spy_load, mod_easymrcp_spy_shutdown, NULL);

/* 应用函数声明 */
SWITCH_STANDARD_APP(easymrcp_spy_start_function);
SWITCH_STANDARD_APP(easymrcp_spy_stop_function);

/* MediaBug回调函数 */
static switch_bool_t easymrcp_spy_callback(switch_media_bug_t *bug, void *user_data, switch_abc_type_t type);

/* RTP相关函数 */
static int rtp_socket_init(rtp_info_t *rtp_info);
static void rtp_send_frame(rtp_info_t *rtp_info, switch_frame_t *frame);
static void rtp_socket_close(rtp_info_t *rtp_info);

/* 模块加载函数 */
SWITCH_MODULE_LOAD_FUNCTION(mod_easymrcp_spy_load)
{
    switch_application_interface_t *app_interface;
    
    *module_interface = switch_loadable_module_create_module_interface(pool, modname);
    
    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_NOTICE, "EasyMrcp Spy module loaded.\n");
    
    SWITCH_ADD_APP(app_interface, "easymrcp_spy_start", "Start EasyMrcp Spy", 
                   "Start EasyMrcp Spy", easymrcp_spy_start_function, "", SAF_NONE);
    SWITCH_ADD_APP(app_interface, "easymrcp_spy_stop", "Stop EasyMrcp Spy", 
                   "Stop EasyMrcp Spy", easymrcp_spy_stop_function, "", SAF_NONE);
    
    return SWITCH_STATUS_SUCCESS;
}

/* 模块卸载函数 */
SWITCH_MODULE_SHUTDOWN_FUNCTION(mod_easymrcp_spy_shutdown)
{
    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_NOTICE, "EasyMrcp Spy module shutdown.\n");
    return SWITCH_STATUS_SUCCESS;
}

/* 启动EasyMrcp Spy */
SWITCH_STANDARD_APP(easymrcp_spy_start_function)
{
    switch_media_bug_t *bug;
    switch_channel_t *channel;
    rtp_info_t *rtp_info;
    const char *target_ip = NULL;
    
    channel = switch_core_session_get_channel(session);
    
    /* 检查是否已经存在EasyMrcp Spy */
    if (switch_channel_get_private(channel, "easymrcp_spy")) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_WARNING, 
                         "EasyMrcp Spy already running on this channel.\n");
        return;
    }
    
    /* 分配内存 */
    rtp_info = (rtp_info_t *)malloc(sizeof(rtp_info_t));
    if (!rtp_info) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Failed to allocate memory for RTP info.\n");
        return;
    }
    
    /* 初始化RTP信息 */
    memset(rtp_info, 0, sizeof(rtp_info_t));
    
    /* 获取目标IP地址和端口参数 格式: IP:PORT */
    if (data && !zstr(data)) {
        char *ip_port = strdup(data);
        char *port_str = strchr(ip_port, ':');
        
        if (port_str) {
            *port_str = '\0';
            port_str++;
            target_ip = ip_port;
            rtp_info->dest_port = atoi(port_str);
        } else {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                             "Invalid format. Expected IP:PORT, got: %s\n", data);
            free(rtp_info);
            return;
        }
        
        /* 保存目标IP */
        switch_copy_string(rtp_info->target_ip, target_ip, sizeof(rtp_info->target_ip));
        
        /* 释放临时分配的内存 */
        free(ip_port);
    } else {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "No target IP:PORT provided.\n");
        free(rtp_info);
        return;
    }
    rtp_info->seq_num = 1;
    rtp_info->timestamp = 0;
    rtp_info->ssrc = rand() & 0xFFFFFFFF;
    
    /* 获取UUID */
    switch_copy_string(rtp_info->uuid, switch_core_session_get_uuid(session), sizeof(rtp_info->uuid));
    
    /* 初始化RTP socket并获取端口 */
    if (rtp_socket_init(rtp_info) != SWITCH_STATUS_SUCCESS) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Failed to initialize RTP socket.\n");
        free(rtp_info);
        return;
    }
    
    /* 添加MediaBug */
    if (switch_core_media_bug_add(session, "easymrcp_spy", NULL,
                                  easymrcp_spy_callback, rtp_info, 0, 
                                  SMBF_READ_STREAM | SMBF_READ_PING,
                                  &bug) != SWITCH_STATUS_SUCCESS) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Failed to add EasyMrcp Spy.\n");
        rtp_socket_close(rtp_info);
        free(rtp_info);
        return;
    }
    
    /* 保存信息到channel */
    switch_channel_set_private(channel, "easymrcp_spy", rtp_info);
    
    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, 
                     "EasyMrcp Spy started for UUID: %s, target: %s:%d\n", 
                     rtp_info->uuid, rtp_info->target_ip, rtp_info->dest_port);
}

/* 停止EasyMrcp Spy */
SWITCH_STANDARD_APP(easymrcp_spy_stop_function)
{
    switch_channel_t *channel;
    rtp_info_t *rtp_info;
    
    channel = switch_core_session_get_channel(session);
    rtp_info = (rtp_info_t *)switch_channel_get_private(channel, "easymrcp_spy");
    
    if (rtp_info) {
        switch_core_media_bug_remove_all_function(session, "easymrcp_spy");
        switch_channel_set_private(channel, "easymrcp_spy", NULL);
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, 
                         "EasyMrcp Spy stopped for UUID: %s\n", rtp_info->uuid);
    }
}

/* MediaBug回调函数 */
static switch_bool_t easymrcp_spy_callback(switch_media_bug_t *bug, void *user_data, switch_abc_type_t type)
{
    rtp_info_t *rtp_info = (rtp_info_t *)user_data;
    
    switch (type) {
        case SWITCH_ABC_TYPE_INIT:
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG, 
                             "EasyMrcp Spy initialized for UUID: %s\n", rtp_info->uuid);
            break;
            
        case SWITCH_ABC_TYPE_CLOSE:
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG, 
                             "EasyMrcp Spy closing for UUID: %s\n", rtp_info->uuid);
            rtp_socket_close(rtp_info);
            free(rtp_info);
            break;
            
        case SWITCH_ABC_TYPE_READ:
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG, 
                             "EasyMrcp Spy READ event for UUID: %s\n", rtp_info->uuid);
            
            // 参考FreeSWITCH文档中的方法，使用switch_core_media_bug_read读取音频数据
            {
                uint8_t data[SWITCH_RECOMMENDED_BUFFER_SIZE];
                switch_frame_t frame = { 0 };
                
                frame.data = data;
                frame.buflen = SWITCH_RECOMMENDED_BUFFER_SIZE;
                
                while (switch_core_media_bug_read(bug, &frame, SWITCH_TRUE) == SWITCH_STATUS_SUCCESS && !switch_test_flag((&frame), SFF_CNG)) {
                    if (frame.datalen) {
                        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG, 
                                         "Got READ frame, size: %d\n", frame.datalen);
                        rtp_send_frame(rtp_info, &frame);
                    }
                }
            }
            break;
            
        default:
            break;
    }
    
    return SWITCH_TRUE;
}

/* 初始化RTP socket */
static int rtp_socket_init(rtp_info_t *rtp_info)
{
    struct sockaddr_in local_addr;
    socklen_t addr_len = sizeof(local_addr);
    
    /* 创建UDP socket */
    rtp_info->sock_fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (rtp_info->sock_fd < 0) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Failed to create socket.\n");
        return SWITCH_STATUS_FALSE;
    }
    
    /* 绑定到本地地址，让系统自动分配端口 */
    memset(&local_addr, 0, sizeof(local_addr));
    local_addr.sin_family = AF_INET;
    local_addr.sin_addr.s_addr = INADDR_ANY;
    local_addr.sin_port = 0;  /* 让系统自动分配端口 */
    
    if (bind(rtp_info->sock_fd, (struct sockaddr*)&local_addr, sizeof(local_addr)) < 0) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Failed to bind socket.\n");
        close(rtp_info->sock_fd);
        return SWITCH_STATUS_FALSE;
    }
    
    /* 获取系统分配的端口号 */
    if (getsockname(rtp_info->sock_fd, (struct sockaddr*)&local_addr, &addr_len) < 0) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Failed to get socket name.\n");
        close(rtp_info->sock_fd);
        return SWITCH_STATUS_FALSE;
    }
    
    rtp_info->rtp_port = ntohs(local_addr.sin_port);
    
    /* 设置目标地址 */
    memset(&rtp_info->dest_addr, 0, sizeof(rtp_info->dest_addr));
    rtp_info->dest_addr.sin_family = AF_INET;
    rtp_info->dest_addr.sin_port = htons(rtp_info->dest_port);
    
    if (inet_pton(AF_INET, rtp_info->target_ip, &rtp_info->dest_addr.sin_addr) <= 0) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Invalid IP address: %s\n", rtp_info->target_ip);
        close(rtp_info->sock_fd);
        return SWITCH_STATUS_FALSE;
    }
    
    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, 
                     "RTP socket initialized, target: %s:%d\n", 
                     rtp_info->target_ip, rtp_info->dest_port);
    
    return SWITCH_STATUS_SUCCESS;
}



/* 快速A-Law编码 - 高性能位操作实现 */
static inline uint8_t pcm_to_alaw(int16_t pcm)
{
    uint8_t sign = 0x00;
    uint16_t linear;
    uint8_t aval;
    
    /* 处理符号位 */
    if (pcm < 0) {
        sign = 0x80;
        linear = (~pcm) >> 3;  /* 负数转正并右移3位 */
    } else {
        linear = pcm >> 3;     /* 正数直接右移3位 */
    }
    
    /* 限制到12位范围 */
    if (linear > 4095) linear = 4095;
    
    /* 快速段查找和量化 - 使用位操作优化 */
    if (linear < 32) {
        /* 段0 */
        aval = linear >> 1;
    } else if (linear < 64) {
        /* 段1 */
        aval = 0x10 | ((linear >> 2) & 0x0F);
    } else if (linear < 128) {
        /* 段2 */
        aval = 0x20 | ((linear >> 3) & 0x0F);
    } else if (linear < 256) {
        /* 段3 */
        aval = 0x30 | ((linear >> 4) & 0x0F);
    } else if (linear < 512) {
        /* 段4 */
        aval = 0x40 | ((linear >> 5) & 0x0F);
    } else if (linear < 1024) {
        /* 段5 */
        aval = 0x50 | ((linear >> 6) & 0x0F);
    } else if (linear < 2048) {
        /* 段6 */
        aval = 0x60 | ((linear >> 7) & 0x0F);
    } else {
        /* 段7 */
        aval = 0x70 | ((linear >> 8) & 0x0F);
    }
    
    return (aval ^ 0x55) | sign;
}

/* 发送RTP帧 */
static void rtp_send_frame(rtp_info_t *rtp_info, switch_frame_t *frame)
{
    rtp_header_t rtp_header;
    uint8_t rtp_packet[1024];
    uint8_t *payload_data;
    int header_size = sizeof(rtp_header_t);
    int packet_size;
    int payload_size = 0;
    
    if (!frame || !frame->data || frame->datalen == 0) {
        return;
    }
    
    /* MediaBug获取的音频数据都是PCM格式，直接转换为G.711A */
    int16_t *pcm_data = (int16_t*)frame->data;
    int samples = frame->datalen / 2;  /* 16位PCM，每个样本2字节 */
    static uint8_t alaw_buffer[1024];
    
    /* 检查缓冲区大小 */
    if (samples > sizeof(alaw_buffer)) {
        samples = sizeof(alaw_buffer);
    }
    
    /* 转换PCM到G.711A */
    for (int i = 0; i < samples; i++) {
        alaw_buffer[i] = pcm_to_alaw(pcm_data[i]);
    }
    
    payload_data = alaw_buffer;
    payload_size = samples;
    
    /* 构造RTP头 */
    memset(&rtp_header, 0, sizeof(rtp_header));
    rtp_header.vpxcc = 0x80;  /* V=2, P=0, X=0, CC=0 */
    rtp_header.mpt = 8;       /* M=0, PT=8 (G711A) */
    rtp_header.sequence_number = htons(rtp_info->seq_num++);
    rtp_header.timestamp = htonl(rtp_info->timestamp);
    rtp_header.ssrc = htonl(rtp_info->ssrc);
    
    /* 构造RTP包 */
    memcpy(rtp_packet, &rtp_header, header_size);
    
    /* 确保不超过缓冲区 */
    if (payload_size > sizeof(rtp_packet) - header_size) {
        payload_size = sizeof(rtp_packet) - header_size;
    }
    
    memcpy(rtp_packet + header_size, payload_data, payload_size);
    packet_size = header_size + payload_size;
    
    /* 发送RTP包 */
    if (sendto(rtp_info->sock_fd, rtp_packet, packet_size, 0, 
               (struct sockaddr*)&rtp_info->dest_addr, 
               sizeof(rtp_info->dest_addr)) < 0) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, 
                         "Failed to send RTP packet.\n");
    } else {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG, 
                         "Sent RTP packet: seq=%d, ts=%u, size=%d\n", 
                         rtp_info->seq_num-1, rtp_info->timestamp, packet_size);
    }
    
    /* 根据G.711A的样本数更新时间戳 (8KHz采样率) */
    rtp_info->timestamp += payload_size;
}

/* 关闭RTP socket */
static void rtp_socket_close(rtp_info_t *rtp_info)
{
    if (rtp_info && rtp_info->sock_fd > 0) {
        close(rtp_info->sock_fd);
        rtp_info->sock_fd = 0;
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, 
                         "RTP socket closed for UUID: %s\n", 
                         rtp_info->uuid);
    }
} 