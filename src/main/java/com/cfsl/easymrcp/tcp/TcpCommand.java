package com.cfsl.easymrcp.tcp;

/**
 * TCP命令数据模型
 */
public class TcpCommand {
    private String id;
    private String command;
    private Object data;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
} 