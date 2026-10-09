package com.devicewatch.ingest;

import com.devicewatch.device.Device;
import com.devicewatch.device.DeviceMapper;
import com.devicewatch.kafka.KafkaProducerService;
import com.devicewatch.metric.MetricDef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * 设备模拟器（demo 模式）：每 2 秒为每台设备生成各测点数据。
 * - 正常值：基线 + 高斯噪声
 * - 随机故障注入：小概率让某测点持续飙高，演示告警全链路
 *
 * Kafka 模式（devicewatch.kafka.enabled=true）下走 Kafka Topic，
 * 否则直调接入服务（零依赖演示）。
 */
@Component
@Profile("demo")
public class DeviceSimulator {
    private final DeviceMapper deviceMapper;
    private final TelemetryIngestService ingestService;
    private final KafkaProducerService kafkaProducerService;
    private final Random random = new Random();

    @Value("${devicewatch.kafka.enabled:false}")
    private boolean kafkaEnabled;
    @Value("${devicewatch.simulator.fault-rate:0.06}")
    private double faultRate;

    /** 故障注入状态：deviceCode:metric -> 剩余故障次数 */
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> faults =
            new java.util.concurrent.ConcurrentHashMap<>();

    public DeviceSimulator(DeviceMapper deviceMapper, TelemetryIngestService ingestService,
                          KafkaProducerService kafkaProducerService) {
        this.deviceMapper = deviceMapper;
        this.ingestService = ingestService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Scheduled(fixedRate = 2000)
    public void tick() {
        List<Device> devices = deviceMapper.selectList(null);
        long now = System.currentTimeMillis();
        for (Device d : devices) {
            for (MetricDef md : MetricDef.ofType(d.getType())) {
                double value = nextValue(d.getCode(), md);
                if (kafkaEnabled) {
                    kafkaProducerService.send(d.getCode(), md.code, value, now);
                } else {
                    ingestService.process(d.getCode(), md.code, value, now);
                }
            }
        }
    }

    private double nextValue(String deviceCode, MetricDef md) {
        String key = deviceCode + ":" + md.code;
        Integer left = faults.get(key);
        if (left != null && left > 0) {
            faults.put(key, left - 1);
            // 故障：1.6~2.2 倍基线，持续多拍，足以触发"连续超标"规则
            return md.base * (1.6 + random.nextDouble() * 0.6);
        }
        if (random.nextDouble() < faultRate) {
            faults.put(key, 8 + random.nextInt(8));
            return md.base * 1.7;
        }
        double v = md.base + random.nextGaussian() * md.noise;
        return Math.max(0, v);
    }
}
