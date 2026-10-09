package com.devicewatch.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 内存 token 会话（单机 demo 足够；生产可换 Redis） */
@Component
public class TokenStore {
    private final Map<String, SysUser> sessions = new ConcurrentHashMap<>();

    public String create(SysUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, user);
        return token;
    }

    public SysUser get(String token) {
        return token == null ? null : sessions.get(token);
    }

    public void remove(String token) {
        sessions.remove(token);
    }
}
