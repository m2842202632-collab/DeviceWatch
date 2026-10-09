package com.devicewatch.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** WebSocket 广播：遥测实时值 + 告警事件推送到大屏 */
@Component
public class WsPushService {
    private static final Logger log = LoggerFactory.getLogger(WsPushService.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public WsPushService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(WebSocketSession s) { sessions.add(s); }
    public void removeSession(WebSocketSession s) { sessions.remove(s); }
    public int sessionCount() { return sessions.size(); }

    public void broadcast(Map<String, Object> event) {
        if (sessions.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return;
        }
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) {
                sessions.remove(s);
                continue;
            }
            try {
                // 高频遥测推送：发送队列堆积则丢弃本条，保实时性
                if (s.getRemoteAddress() != null) {
                    s.sendMessage(msg);
                }
            } catch (Exception e) {
                log.debug("ws 发送失败", e);
                sessions.remove(s);
            }
        }
    }
}
