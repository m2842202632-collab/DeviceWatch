package com.devicewatch.common;

/** 统一 API 响应体 */
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0; r.msg = "ok"; r.data = data;
        return r;
    }

    public static <T> Result<T> ok() { return ok(null); }

    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<>();
        r.code = 1; r.msg = msg;
        return r;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
