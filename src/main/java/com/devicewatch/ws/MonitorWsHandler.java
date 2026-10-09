package com.devicewatch.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MonitorWsHandler extends TextWebSocketHandler {
    private final WsPushService wsPushService;

    public MonitorWsHandler(WsPushService wsPushService) {
        this.wsPushService = wsPushService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        wsPushService.addSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        wsPushService.removeSession(session);
    }
}
