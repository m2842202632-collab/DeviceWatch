package com.devicewatch;

import com.devicewatch.alert.AlertMapper;
import com.devicewatch.alert.AlertRecord;
import com.devicewatch.device.Device;
import com.devicewatch.device.DeviceMapper;
import com.devicewatch.ingest.TelemetryIngestService;
import com.devicewatch.kafka.TelemetryKafkaConsumer;
import com.devicewatch.rule.AlertRule;
import com.devicewatch.rule.AlertRuleMapper;
import com.devicewatch.rule.RuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka 消费路径测试：不启动 broker，直接调用 @KafkaListener 方法，
 * 验证 JSON 解析 -> 接入 -> 规则引擎全链路；毒消息不抛异常且照常 ack。
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaConsumerPathTest {
    @Autowired TelemetryIngestService ingestService;
    @Autowired ObjectMapper objectMapper;
    @Autowired DeviceMapper deviceMapper;
    @Autowired AlertRuleMapper ruleMapper;
    @Autowired AlertMapper alertMapper;
    @Autowired RuleService ruleService;

    private TelemetryKafkaConsumer consumer;
    private Device device;

    @BeforeEach
    void setup() {
        consumer = new TelemetryKafkaConsumer(ingestService, objectMapper);
        alertMapper.delete(null);
        ruleMapper.delete(null);
        deviceMapper.delete(null);

        device = new Device();
        device.setCode("KAFKA-PUMP-01");
        device.setName("Kafka测试泵");
        device.setType("PUMP");
        device.setStatus("ONLINE");
        device.setCreatedAt(LocalDateTime.now());
        deviceMapper.insert(device);

        AlertRule rule = new AlertRule();
        rule.setName("Kafka温度规则");
        rule.setDeviceId(device.getId());
        rule.setMetricCode("temperature");
        rule.setOperator("GT");
        rule.setThreshold(80.0);
        rule.setDurationTimes(1);
        rule.setLevel("CRITICAL");
        rule.setSilenceMinutes(10);
        rule.setEscalateMinutes(0);
        rule.setEnabled(true);
        rule.setCreatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        ruleService.refreshCache();
    }

    private Acknowledgment ack() {
        AtomicBoolean done = new AtomicBoolean(false);
        return new Acknowledgment() {
            @Override public void acknowledge() { done.set(true); }
            public boolean isDone() { return done.get(); }
        };
    }

    @Test
    void listenerProcessesMessageAndAcks() throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of(
                "deviceId", device.getCode(), "metric", "temperature",
                "value", 99.5, "ts", System.currentTimeMillis()));
        Acknowledgment a = ack();
        consumer.listen(new ConsumerRecord<>("device-telemetry", 0, 0, device.getCode(), json), a);
        assertEquals(1, alertMapper.selectCount(null));
        AlertRecord r = alertMapper.selectList(null).get(0);
        assertEquals("CRITICAL", r.getLevel());
    }

    @Test
    void poisonMessageIsSkippedButAcked() {
        Acknowledgment a = ack();
        assertDoesNotThrow(() ->
                consumer.listen(new ConsumerRecord<>("device-telemetry", 0, 0, "x", "{broken json"), a));
        assertEquals(0, alertMapper.selectCount(null));
    }

    @Test
    void unknownDeviceIsSkipped() throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of(
                "deviceId", "NOT-EXIST", "metric", "temperature",
                "value", 99.5, "ts", System.currentTimeMillis()));
        assertDoesNotThrow(() ->
                consumer.listen(new ConsumerRecord<>("device-telemetry", 0, 0, "NOT-EXIST", json), ack()));
        assertEquals(0, alertMapper.selectCount(null));
    }
}
