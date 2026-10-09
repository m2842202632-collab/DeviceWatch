package com.devicewatch.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devicewatch.alert.AlertMapper;
import com.devicewatch.alert.AlertRecord;
import com.devicewatch.device.Device;
import com.devicewatch.device.DeviceMapper;
import com.devicewatch.ingest.TelemetryMapper;
import com.devicewatch.ingest.TelemetryRecord;
import com.devicewatch.metric.MetricDef;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 看板统计。时间维度聚合全在 Java 侧做（H2/MySQL 双兼容，
 * 不写方言相关的日期函数）。
 */
@Service
public class DashboardService {
    private final DeviceMapper deviceMapper;
    private final AlertMapper alertMapper;
    private final TelemetryMapper telemetryMapper;

    public DashboardService(DeviceMapper deviceMapper, AlertMapper alertMapper,
                           TelemetryMapper telemetryMapper) {
        this.deviceMapper = deviceMapper;
        this.alertMapper = alertMapper;
        this.telemetryMapper = telemetryMapper;
    }

    /** 实时快照：设备 + 最新测点值 + 状态 */
    public List<Map<String, Object>> snapshot() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().orderByAsc(Device::getCode));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Device d : devices) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("code", d.getCode());
            m.put("name", d.getName());
            m.put("type", d.getType());
            m.put("typeName", MetricDef.typeName(d.getType()));
            m.put("location", d.getLocation());
            m.put("status", d.getStatus());
            m.put("lastSeenAt", d.getLastSeenAt());
            m.put("firingCount", alertMapper.countFiringByDevice(d.getId()));
            // 一次查询取设备近期全量遥测（倒序），按测点取第一条即最新值：N+1 -> 每台 1 次查询
            Map<String, Double> byMetric = new HashMap<>();
            for (TelemetryRecord r : telemetryMapper.recentByDevice(d.getId(),
                    LocalDateTime.now().minusMinutes(10))) {
                byMetric.putIfAbsent(r.getMetricCode(), r.getMetricValue());
            }
            // 按测点定义顺序输出，前端展示稳定
            Map<String, Double> latest = new LinkedHashMap<>();
            for (MetricDef md : MetricDef.ofType(d.getType())) {
                Double v = byMetric.get(md.code);
                if (v != null) latest.put(md.code, v);
            }
            m.put("latest", latest);
            rows.add(m);
        }
        return rows;
    }

    /** 总览数字 */
    public Map<String, Object> overview() {
        List<Device> devices = deviceMapper.selectList(null);
        long online = devices.stream().filter(d -> "ONLINE".equals(d.getStatus())).count();
        long alarm = devices.stream().filter(d -> "ALARM".equals(d.getStatus())).count();
        long offline = devices.stream().filter(d -> "OFFLINE".equals(d.getStatus())).count();
        long firing = alertMapper.selectCount(
                new LambdaQueryWrapper<AlertRecord>().eq(AlertRecord::getStatus, "FIRING"));
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long todayAlerts = alertMapper.selectCount(
                new LambdaQueryWrapper<AlertRecord>().ge(AlertRecord::getFirstFiredAt, today));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceTotal", devices.size());
        m.put("online", online);
        m.put("alarm", alarm);
        m.put("offline", offline);
        m.put("firing", firing);
        m.put("todayAlerts", todayAlerts);
        return m;
    }

    /** 最近 24 小时告警趋势（按小时） */
    public List<Map<String, Object>> alertTrend() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(24);
        List<AlertRecord> records = alertMapper.selectList(
                new LambdaQueryWrapper<AlertRecord>().ge(AlertRecord::getFirstFiredAt, start));
        Map<String, long[]> buckets = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:00");
        for (int i = 23; i >= 0; i--) {
            buckets.put(end.minusHours(i).format(fmt), new long[2]);
        }
        for (AlertRecord r : records) {
            String k = r.getFirstFiredAt().format(fmt);
            long[] b = buckets.get(k);
            if (b == null) continue;
            if ("CRITICAL".equals(r.getLevel())) b[1]++;
            else b[0]++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : buckets.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.getKey());
            m.put("warn", e.getValue()[0]);
            m.put("critical", e.getValue()[1]);
            out.add(m);
        }
        return out;
    }

    /** 设备健康度：100 分起扣，告警中 -15/条，今日已发 -3/条 */
    public List<Map<String, Object>> deviceHealth() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().orderByAsc(Device::getCode));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Device d : devices) {
            int firing = alertMapper.countFiringByDevice(d.getId());
            long todayCount = alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                    .eq(AlertRecord::getDeviceId, d.getId())
                    .ge(AlertRecord::getFirstFiredAt, today));
            int score = (int) Math.max(0, 100 - firing * 15 - todayCount * 3);
            if ("OFFLINE".equals(d.getStatus())) score = Math.min(score, 40);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.getCode());
            m.put("name", d.getName());
            m.put("status", d.getStatus());
            m.put("score", score);
            m.put("firing", firing);
            m.put("todayAlerts", todayCount);
            out.add(m);
        }
        out.sort((a, b) -> Integer.compare((int) a.get("score"), (int) b.get("score")));
        return out;
    }

    /** MTBF / MTTR（小时）：按设备统计已关闭告警 */
    public List<Map<String, Object>> mtbf() {
        List<Device> devices = deviceMapper.selectList(null);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Device d : devices) {
            List<AlertRecord> resolved = alertMapper.selectList(
                    new LambdaQueryWrapper<AlertRecord>()
                            .eq(AlertRecord::getDeviceId, d.getId())
                            .eq(AlertRecord::getStatus, "RESOLVED")
                            .orderByAsc(AlertRecord::getFirstFiredAt));
            double mttr = 0, mtbf = 0;
            if (!resolved.isEmpty()) {
                double totalRepairMin = 0;
                int n = 0;
                for (AlertRecord r : resolved) {
                    if (r.getResolvedAt() != null && r.getFirstFiredAt() != null) {
                        totalRepairMin += java.time.Duration.between(
                                r.getFirstFiredAt(), r.getResolvedAt()).toMinutes();
                        n++;
                    }
                }
                if (n > 0) mttr = totalRepairMin / n / 60.0;
                if (resolved.size() > 1) {
                    AlertRecord first = resolved.get(0);
                    AlertRecord last = resolved.get(resolved.size() - 1);
                    double spanHours = java.time.Duration.between(
                            first.getFirstFiredAt(), last.getFirstFiredAt()).toMinutes() / 60.0;
                    mtbf = spanHours / (resolved.size() - 1);
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.getCode());
            m.put("name", d.getName());
            m.put("alertCount", resolved.size());
            m.put("mttrHours", Math.round(mttr * 100.0) / 100.0);
            m.put("mtbfHours", Math.round(mtbf * 100.0) / 100.0);
            out.add(m);
        }
        return out;
    }

    /** 历史曲线 */
    public Map<String, Object> history(Long deviceId, String metricCode, int minutes) {
        Device d = deviceMapper.selectById(deviceId);
        if (d == null) throw new IllegalArgumentException("设备不存在");
        List<TelemetryRecord> list = telemetryMapper.history(deviceId, metricCode,
                LocalDateTime.now().minusMinutes(minutes));
        // 降采样：最多 300 点
        List<TelemetryRecord> sampled = list;
        if (list.size() > 300) {
            int step = list.size() / 300;
            sampled = new ArrayList<>();
            for (int i = 0; i < list.size(); i += step) sampled.add(list.get(i));
        }
        MetricDef md = MetricDef.find(d.getType(), metricCode);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceCode", d.getCode());
        m.put("metricName", md == null ? metricCode : md.name);
        m.put("unit", md == null ? "" : md.unit);
        m.put("points", sampled.stream().map(r -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("ts", r.getTs().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            p.put("value", r.getMetricValue());
            return p;
        }).collect(Collectors.toList()));
        return m;
    }
}
