package com.devicewatch.config;

import com.devicewatch.ws.MonitorWsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final MonitorWsHandler monitorWsHandler;

    public WebSocketConfig(MonitorWsHandler monitorWsHandler) {
        this.monitorWsHandler = monitorWsHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(monitorWsHandler, "/ws/monitor").setAllowedOrigins("*");
    }
}
