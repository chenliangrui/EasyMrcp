package com.cfsl.easymrcp.tcp;

/**
 * TCP响应数据模型
 */
public class TcpResponse {
    private String id;
    private int code;
    private String message;
    private Object data;

    public TcpResponse() {
    }

    public TcpResponse(String id, int code, String message, Object data) {
        this.id = id;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static TcpResponse success(String id, Object data) {
        return new TcpResponse(id, 200, "Success", data);
    }

    public static TcpResponse error(String id, String message) {
        return new TcpResponse(id, 500, message, null);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
} 