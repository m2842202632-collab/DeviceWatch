package com.devicewatch.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devicewatch.common.PageResult;
import com.devicewatch.device.Device;
import com.devicewatch.device.DeviceMapper;
import com.devicewatch.metric.MetricDef;
import com.devicewatch.rule.AlertRule;
import com.devicewatch.ws.WsPushService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警服务：触发 / 静默抑制 / 确认 / 恢复 / 升级 / 离线告警。
 *
 * 风暴抑制：同一规则+设备在 silenceMinutes 内重复触发，不新增记录、
 * 不推送，只在原记录上累加 suppressedCount。
 */
@Service
public class AlertService {
    /** 离线告警用的虚拟 ruleId */
    public static final long OFFLINE_RULE_ID = -1L;

    private final AlertMapper alertMapper;
    private final DeviceMapper deviceMapper;
    private final WsPushService wsPushService;
    /** (ruleId:deviceId) -> 上次真实触发时间，用于静默期判断 */
    private final ConcurrentHashMap<String, Long> lastFireTime = new ConcurrentHashMap<>();

    public AlertService(AlertMapper alertMapper, DeviceMapper deviceMapper, WsPushService wsPushService) {
        this.alertMapper = alertMapper;
        this.deviceMapper = deviceMapper;
        this.wsPushService = wsPushService;
    }

    public void clearState() {
        lastFireTime.clear();
    }

    /**
     * 规则引擎判定"达到触发条件"后调用。
     * @return true=真实触发（新增/刷新记录并推送），false=被静默期抑制
     */
    public boolean fireOrSuppress(AlertRule rule, Device device, String metricCode, double value) {
        String key = rule.getId() + ":" + device.getId();
        long now = System.currentTimeMillis();
        Long last = lastFireTime.get(key);
        long silenceMs = Math.max(0, rule.getSilenceMinutes()) * 60_000L;
        LocalDateTime ts = LocalDateTime.now();
        if (last != null && now - last < silenceMs) {
            alertMapper.incrementSuppressed(rule.getId(), device.getId(), ts);
            return false;
        }
        lastFireTime.put(key, now);
        AlertRecord active = alertMapper.findActive(rule.getId(), device.getId());
        if (active == null) {
            AlertRecord r = new AlertRecord();
            r.setRuleId(rule.getId());
            r.setDeviceId(device.getId());
            r.setMetricCode(metricCode);
            r.setLevel(rule.getLevel());
            r.setStatus("FIRING");
            r.setTriggerValue(value);
            r.setMaxValue(value);
            r.setFireCount(1);
            r.setSuppressedCount(0);
            r.setFirstFiredAt(ts);
            r.setLastFiredAt(ts);
            alertMapper.insert(r);
            active = r;
        } else {
            alertMapper.refreshOnFire(rule.getId(), device.getId(), value, ts);
            active = alertMapper.findActive(rule.getId(), device.getId());
        }
        refreshDeviceStatus(device.getId());
        pushAlertEvent(active, device, rule.getName(), metricCode, value);
        return true;
    }

    public boolean hasFiringAlert(Long ruleId, Long deviceId) {
        return alertMapper.findActive(ruleId, deviceId) != null;
    }

    /** 测点恢复正常且持续几次后，自动关闭告警 */
    public void autoResolve(Long ruleId, Long deviceId) {
        AlertRecord active = alertMapper.findActive(ruleId, deviceId);
        if (active == null) return;
        active.setStatus("RESOLVED");
        active.setResolvedAt(LocalDateTime.now());
        active.setRemark("测点恢复正常，系统自动关闭");
        alertMapper.updateById(active);
        lastFireTime.remove(ruleId + ":" + deviceId);
        refreshDeviceStatus(deviceId);
        pushStatusEvent(active);
    }

    public void ack(Long id, String by) {
        AlertRecord r = alertMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("告警不存在");
        if (!"FIRING".equals(r.getStatus())) throw new IllegalArgumentException("只有告警中的记录可确认");
        r.setStatus("ACKED");
        r.setAckBy(by);
        r.setAckAt(LocalDateTime.now());
        alertMapper.updateById(r);
        pushStatusEvent(r);
    }

    public void resolve(Long id, String by, String remark) {
        AlertRecord r = alertMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("告警不存在");
        if ("RESOLVED".equals(r.getStatus())) throw new IllegalArgumentException("告警已关闭");
        r.setStatus("RESOLVED");
        r.setResolvedAt(LocalDateTime.now());
        r.setRemark(remark);
        alertMapper.updateById(r);
        lastFireTime.remove(r.getRuleId() + ":" + r.getDeviceId());
        refreshDeviceStatus(r.getDeviceId());
        pushStatusEvent(r);
    }

    /** 定时任务：WARN 告警长时间未确认自动升级为 CRITICAL */
    public int escalateDue(int escalateMinutes) {
        if (escalateMinutes <= 0) return 0;
        LocalDateTime before = LocalDateTime.now().minusMinutes(escalateMinutes);
        List<AlertRecord> due = alertMapper.findEscalationDue(before);
        for (AlertRecord r : due) {
            r.setLevel("CRITICAL");
            alertMapper.updateById(r);
            pushStatusEvent(r);
        }
        return due.size();
    }

    /** 设备离线告警（系统级，ruleId=-1） */
    public void fireOffline(Device device) {
        AlertRecord active = alertMapper.findActive(OFFLINE_RULE_ID, device.getId());
        LocalDateTime ts = LocalDateTime.now();
        if (active == null) {
            AlertRecord r = new AlertRecord();
            r.setRuleId(OFFLINE_RULE_ID);
            r.setDeviceId(device.getId());
            r.setMetricCode("offline");
            r.setLevel("WARN");
            r.setStatus("FIRING");
            r.setFireCount(1);
            r.setSuppressedCount(0);
            r.setFirstFiredAt(ts);
            r.setLastFiredAt(ts);
            r.setRemark("设备心跳超时，判定离线");
            alertMapper.insert(r);
            active = r;
            pushAlertEvent(active, device, "设备离线", "offline", 0);
        }
        device.setStatus("OFFLINE");
        deviceMapper.updateById(device);
    }

    public void resolveOffline(Long deviceId) {
        AlertRecord active = alertMapper.findActive(OFFLINE_RULE_ID, deviceId);
        if (active != null) {
            active.setStatus("RESOLVED");
            active.setResolvedAt(LocalDateTime.now());
            active.setRemark("设备心跳恢复，系统自动关闭");
            alertMapper.updateById(active);
            pushStatusEvent(active);
        }
    }

    /** 根据告警情况刷新设备状态：离线 > 告警中 > 在线 */
    public void refreshDeviceStatus(Long deviceId) {
        Device d = deviceMapper.selectById(deviceId);
        if (d == null) return;
        if ("OFFLINE".equals(d.getStatus())) return; // 离线由心跳任务管理
        int firing = alertMapper.countFiringByDevice(deviceId);
        String target = firing > 0 ? "ALARM" : "ONLINE";
        if (!target.equals(d.getStatus())) {
            d.setStatus(target);
            deviceMapper.updateById(d);
        }
    }

    public PageResult<Map<String, Object>> page(String status, String level, Long deviceId,
                                               long page, long size) {
        LambdaQueryWrapper<AlertRecord> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) w.eq(AlertRecord::getStatus, status);
        if (StringUtils.hasText(level)) w.eq(AlertRecord::getLevel, level);
        if (deviceId != null) w.eq(AlertRecord::getDeviceId, deviceId);
        w.orderByDesc(AlertRecord::getLastFiredAt);
        Page<AlertRecord> p = alertMapper.selectPage(new Page<>(page, size), w);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (AlertRecord r : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            Device d = deviceMapper.selectById(r.getDeviceId());
            m.put("id", r.getId());
            m.put("ruleId", r.getRuleId());
            m.put("deviceId", r.getDeviceId());
            m.put("deviceCode", d == null ? "-" : d.getCode());
            m.put("deviceName", d == null ? "-" : d.getName());
            MetricDef md = d == null ? null : MetricDef.find(d.getType(), r.getMetricCode());
            m.put("metricName", md == null ? r.getMetricCode() : md.name + "(" + md.unit + ")");
            m.put("level", r.getLevel());
            m.put("status", r.getStatus());
            m.put("triggerValue", r.getTriggerValue());
            m.put("maxValue", r.getMaxValue());
            m.put("fireCount", r.getFireCount());
            m.put("suppressedCount", r.getSuppressedCount());
            m.put("firstFiredAt", r.getFirstFiredAt());
            m.put("lastFiredAt", r.getLastFiredAt());
            m.put("ackBy", r.getAckBy());
            m.put("ackAt", r.getAckAt());
            m.put("resolvedAt", r.getResolvedAt());
            m.put("remark", r.getRemark());
            rows.add(m);
        }
        return PageResult.of(rows, p.getTotal(), page, size);
    }

    private void pushAlertEvent(AlertRecord r, Device device, String ruleName,
                               String metricCode, double value) {
        Map<String, Object> e = new HashMap<>();
        e.put("type", "alert");
        e.put("id", r.getId());
        e.put("deviceCode", device.getCode());
        e.put("deviceName", device.getName());
        e.put("ruleName", ruleName);
        e.put("metricCode", metricCode);
        e.put("value", value);
        e.put("level", r.getLevel());
        wsPushService.broadcast(e);
    }

    private void pushStatusEvent(AlertRecord r) {
        Map<String, Object> e = new HashMap<>();
        e.put("type", "alert-status");
        e.put("id", r.getId());
        e.put("status", r.getStatus());
        e.put("level", r.getLevel());
        wsPushService.broadcast(e);
    }
}
