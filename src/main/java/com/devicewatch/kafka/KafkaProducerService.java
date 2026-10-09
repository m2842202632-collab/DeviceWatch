package com.devicewatch.kafka;

import com.devicewatch.ingest.Telemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 遥测生产者。key=deviceId：Kafka 按 key 做分区，
 * 同一设备的数据永远落在同一分区 → 分区内有序，规则引擎的
 * "连续超标 N 次"判定才不会被乱序打乱。
 */
@Component
public class KafkaProducerService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(String deviceCode, String metricCode, double value, long tsMillis) {
        try {
            Telemetry t = new Telemetry();
            t.setDeviceId(deviceCode);
            t.setMetric(metricCode);
            t.setValue(value);
            t.setTs(tsMillis);
            kafkaTemplate.send("device-telemetry", deviceCode, objectMapper.writeValueAsString(t));
        } catch (Exception e) {
            throw new IllegalStateException("发送遥测消息失败", e);
        }
    }
}
