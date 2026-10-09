package com.devicewatch.auth;

import com.devicewatch.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = request.getHeader("X-Token");
        SysUser user = tokenStore.get(token);
        if (user == null) {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail("未登录或登录已过期")));
            return false;
        }
        request.setAttribute("loginUser", user);
        return true;
    }
}
