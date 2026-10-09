package com.devicewatch.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka 配置。仅当 devicewatch.kafka.enabled=true 时生效。
 * 分区策略：生产者按 deviceId 做 key，保证同一设备的数据落在同一分区、全局有序。
 * 消费端：手动提交 offset + 业务幂等，做到"至少一次投递、业务不重不漏"。
 */
@Configuration
@ConditionalOnProperty(name = "devicewatch.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public NewTopic deviceTelemetryTopic() {
        // 6 分区；key=deviceId，保证单设备有序
        return new NewTopic("device-telemetry", 6, (short) 1);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 手动立即提交：业务处理成功后才 ack，避免"丢消息"
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);
        return factory;
    }
}
