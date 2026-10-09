package com.devicewatch;

import com.devicewatch.alert.AlertMapper;
import com.devicewatch.alert.AlertRecord;
import com.devicewatch.alert.AlertService;
import com.devicewatch.device.Device;
import com.devicewatch.device.DeviceMapper;
import com.devicewatch.ingest.TelemetryIngestService;
import com.devicewatch.rule.AlertRule;
import com.devicewatch.rule.AlertRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端：遥测接入 -> 规则引擎 -> 告警触发 -> 静默抑制 -> 确认/关闭。
 * 用 H2 + 直调接入层（不依赖 Kafka）。
 */
@SpringBootTest
@ActiveProfiles("test")
class AlertFlowTest {
    @Autowired TelemetryIngestService ingestService;
    @Autowired AlertService alertService;
    @Autowired DeviceMapper deviceMapper;
    @Autowired AlertRuleMapper ruleMapper;
    @Autowired AlertMapper alertMapper;
    @Autowired com.devicewatch.rule.RuleService ruleService;
    @Autowired com.devicewatch.alert.RuleEngine ruleEngine;
    @Autowired com.devicewatch.ingest.TelemetryMapper telemetryMapper;
    @Autowired com.devicewatch.device.DeviceService deviceService;

    private Device device;
    private AlertRule rule;

    @BeforeEach
    void setup() {
        // 清理 DB + 内存状态（上下文在类内共享，必须手动清）
        telemetryMapper.delete(null);
        alertMapper.delete(null);
        ruleMapper.delete(null);
        deviceMapper.delete(null);
        ingestService.refreshDeviceCache();
        ruleEngine.clearState();

        device = new Device();
        device.setCode("TEST-PUMP-01");
        device.setName("测试泵");
        device.setType("PUMP");
        device.setLocation("测试车间");
        device.setStatus("ONLINE");
        device.setCreatedAt(LocalDateTime.now());
        deviceMapper.insert(device);

        rule = new AlertRule();
        rule.setName("测试温度规则");
        rule.setDeviceId(device.getId());
        rule.setMetricCode("temperature");
        rule.setOperator("GT");
        rule.setThreshold(80.0);
        rule.setDurationTimes(2);
        rule.setLevel("WARN");
        rule.setSilenceMinutes(10);
        rule.setEscalateMinutes(0);
        rule.setEnabled(true);
        rule.setCreatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        // RuleService 在启动时缓存了规则，这里手动刷新
        // （通过 AlertFlowTest 拿不到 RuleService？拿得到，见下）
    }

    private void feed(double value) {
        ingestService.process(device.getCode(), "temperature", value, System.currentTimeMillis());
        // ts 唯一键要求每条 ts 不同，sleep 1ms 保险
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
    }

    @Test
    void breachTriggersAlert() {
        ruleService.refreshCache();
        feed(70); // 正常
        assertEquals(0, alertMapper.selectCount(null));
        feed(85); // 第1次超标
        assertEquals(0, alertMapper.selectCount(null));
        feed(86); // 第2次超标 -> 触发
        assertEquals(1, alertMapper.selectCount(null));
        AlertRecord r = alertMapper.selectList(null).get(0);
        assertEquals("FIRING", r.getStatus());
        assertEquals("WARN", r.getLevel());
        assertEquals(86, r.getTriggerValue());
        assertEquals(1, r.getFireCount());
    }

    @Test
    void silenceSuppressesStorm() {
        ruleService.refreshCache();
        feed(85); feed(86); // 触发
        assertEquals(1, alertMapper.selectCount(null));
        // 静默期内继续超标：不新增记录，只累加抑制计数
        for (int i = 0; i < 6; i++) feed(90 + i);
        assertEquals(1, alertMapper.selectCount(null));
        AlertRecord r = alertMapper.selectList(null).get(0);
        assertTrue(r.getSuppressedCount() >= 2, "suppressed=" + r.getSuppressedCount());
    }

    @Test
    void recoveryAutoResolves() {
        ruleService.refreshCache();
        feed(85); feed(86); // 触发
        assertEquals(1, alertMapper.selectCount(null));
        feed(70); feed(71); feed(72); // 连续3次正常 -> 自动关闭
        AlertRecord r = alertMapper.selectList(null).get(0);
        assertEquals("RESOLVED", r.getStatus());
        assertNotNull(r.getResolvedAt());
    }

    @Test
    void ackAndResolve() {
        ruleService.refreshCache();
        feed(85); feed(86);
        AlertRecord r = alertMapper.selectList(null).get(0);
        alertService.ack(r.getId(), "测试员");
        r = alertMapper.selectById(r.getId());
        assertEquals("ACKED", r.getStatus());
        assertEquals("测试员", r.getAckBy());
        alertService.resolve(r.getId(), "测试员", "现场处理完毕");
        r = alertMapper.selectById(r.getId());
        assertEquals("RESOLVED", r.getStatus());
    }

    @Test
    void duplicateTelemetryIsIdempotent() {
        ruleService.refreshCache();
        long ts = System.currentTimeMillis();
        ingestService.process(device.getCode(), "temperature", 70, ts);
        ingestService.process(device.getCode(), "temperature", 70, ts); // 重复投递
        assertEquals(1, telemetryMapper.selectCount(null).intValue());
    }

    @Test
    void offlineAlertFlow() {
        Device d = deviceMapper.selectById(device.getId());
        alertService.fireOffline(d);
        AlertRecord r = alertMapper.findActive(AlertService.OFFLINE_RULE_ID, device.getId());
        assertNotNull(r);
        assertEquals("FIRING", r.getStatus());
        assertEquals("OFFLINE", deviceMapper.selectById(device.getId()).getStatus());
        alertService.resolveOffline(device.getId());
        assertNull(alertMapper.findActive(AlertService.OFFLINE_RULE_ID, device.getId()));
    }

    @Test
    void deletedDeviceStopsIngestAndRecreateGetsFreshId() {
        feed(70);
        assertEquals(1, telemetryMapper.selectCount(null).intValue());
        Long oldId = device.getId();

        // 删除设备后，同编码的遥测必须被丢弃（不能写入已删除的 device_id）
        deviceService.delete(oldId);
        feed(71);
        feed(72);
        assertEquals(1, telemetryMapper.selectCount(null).intValue());

        // 同编码重建后，数据应写入新 id，而非复活旧缓存
        Device nd = new Device();
        nd.setCode(device.getCode());
        nd.setName("重建泵");
        nd.setType("PUMP");
        deviceService.create(nd);
        assertNotEquals(oldId, nd.getId());
        feed(73);
        assertEquals(2, telemetryMapper.selectCount(null).intValue());
        Long newDeviceId = telemetryMapper.selectList(null).stream()
                .map(com.devicewatch.ingest.TelemetryRecord::getDeviceId)
                .filter(id -> !id.equals(oldId))
                .findFirst().orElse(null);
        assertEquals(nd.getId(), newDeviceId);
    }
}
