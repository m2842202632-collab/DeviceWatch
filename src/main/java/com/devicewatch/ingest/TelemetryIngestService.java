package com.devicewatch.ingest;

import com.devicewatch.alert.RuleEngine;
import com.devicewatch.device.Device;
import com.devicewatch.device.DeviceMapper;
import com.devicewatch.ws.WsPushService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遥测数据统一入口。demo 模拟器直接调，Kafka 消费者也调这里。
 * 幂等：telemetry 表对 (device_id, metric_code, ts) 建唯一键，
 * Kafka 重复投递时 DuplicateKeyException 直接丢弃（at-least-once + 业务幂等）。
 */
@Service
public class TelemetryIngestService {
    private final DeviceMapper deviceMapper;
    private final TelemetryMapper telemetryMapper;
    private final RuleEngine ruleEngine;
    private final WsPushService wsPushService;
    /** 设备编码 -> 不可变信息（缓存，避免每条遥测查 DB） */
    private final ConcurrentHashMap<String, DeviceRef> deviceCache = new ConcurrentHashMap<>();

    public TelemetryIngestService(DeviceMapper deviceMapper, TelemetryMapper telemetryMapper,
                                 RuleEngine ruleEngine, WsPushService wsPushService) {
        this.deviceMapper = deviceMapper;
        this.telemetryMapper = telemetryMapper;
        this.ruleEngine = ruleEngine;
        this.wsPushService = wsPushService;
    }

    public void refreshDeviceCache() {
        deviceCache.clear();
    }

    /** 设备不可变信息缓存（id/code/name/type），状态类字段一律走 DB，避免缓存覆盖 */
    private static class DeviceRef {
        final Long id; final String code; final String name; final String type;
        DeviceRef(Long id, String code, String name, String type) {
            this.id = id; this.code = code; this.name = name; this.type = type;
        }
    }

    public void process(String deviceCode, String metricCode, double value, long tsMillis) {
        DeviceRef ref = deviceCache.computeIfAbsent(deviceCode, code -> {
            Device d = deviceMapper.findByCode(code);
            return d == null ? null : new DeviceRef(d.getId(), d.getCode(), d.getName(), d.getType());
        });
        if (ref == null) return;
        LocalDateTime ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMillis), ZoneId.systemDefault());
        // 1. 幂等入库
        TelemetryRecord r = new TelemetryRecord();
        r.setDeviceId(ref.id);
        r.setMetricCode(metricCode);
        r.setMetricValue(value);
        r.setTs(ts);
        try {
            telemetryMapper.insert(r);
        } catch (DuplicateKeyException e) {
            return; // 重复消息，直接丢弃
        }
        // 2. 心跳：只更新 last_seen_at，不碰 status（状态由告警/离线任务维护）
        deviceMapper.touchSeen(ref.id, ts);
        // 3. 规则引擎判定
        Device device = new Device();
        device.setId(ref.id);
        device.setCode(ref.code);
        device.setName(ref.name);
        device.setType(ref.type);
        ruleEngine.evaluate(device, metricCode, value);
        // 4. WebSocket 实时推送
        Map<String, Object> e = new HashMap<>();
        e.put("type", "telemetry");
        e.put("deviceCode", deviceCode);
        e.put("deviceId", ref.id);
        e.put("metric", metricCode);
        e.put("value", Math.round(value * 100.0) / 100.0);
        e.put("ts", tsMillis);
        wsPushService.broadcast(e);
    }
}
