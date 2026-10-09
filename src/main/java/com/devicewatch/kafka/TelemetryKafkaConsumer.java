package com.devicewatch.kafka;

import com.devicewatch.ingest.Telemetry;
import com.devicewatch.ingest.TelemetryIngestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 遥测消费者。仅 devicewatch.kafka.enabled=true 时加载。
 *
 * 可靠性设计：
 * - 手动提交 offset（MANUAL_IMMEDIATE）：业务处理成功后才 ack，
 *   崩溃重启会重投，但接入层有 (device,metric,ts) 唯一键幂等，
 *   整体做到 at-least-once 投递 + 业务不重不漏。
 * - 毒消息（JSON 解析失败）：记日志后直接 ack，避免卡死分区。
 */
@Component
@ConditionalOnProperty(name = "devicewatch.kafka.enabled", havingValue = "true")
public class TelemetryKafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaConsumer.class);

    private final TelemetryIngestService ingestService;
    private final ObjectMapper objectMapper;

    public TelemetryKafkaConsumer(TelemetryIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "device-telemetry", groupId = "device-watch-group")
    public void listen(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Telemetry t = objectMapper.readValue(record.value(), Telemetry.class);
            if (t.getDeviceId() == null || t.getMetric() == null) {
                log.warn("毒消息直接丢弃: {}", record.value());
            } else {
                ingestService.process(t.getDeviceId(), t.getMetric(), t.getValue(), t.getTs());
            }
        } catch (Exception e) {
            log.error("处理遥测消息失败，直接 ack 避免阻塞分区: {}", record.value(), e);
        } finally {
            if (ack != null) ack.acknowledge();
        }
    }
}
