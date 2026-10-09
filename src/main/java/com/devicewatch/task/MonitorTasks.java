package com.devicewatch.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devicewatch.alert.AlertMapper;
import com.devicewatch.alert.AlertRecord;
import com.devicewatch.alert.AlertService;
import com.devicewatch.device.Device;
import com.devicewatch.device.DeviceMapper;
import com.devicewatch.ingest.TelemetryMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台定时任务：
 * - 离线检测：心跳超时的设备标 OFFLINE + 离线告警
 * - 告警升级：WARN 长时间未确认自动升级 CRITICAL
 * - 遥测清理：只保留最近 7 天（历史曲线够用，表不爆炸）
 */
@Component
public class MonitorTasks {
    private final DeviceMapper deviceMapper;
    private final AlertService alertService;
    private final AlertMapper alertMapper;
    private final TelemetryMapper telemetryMapper;

    /** 心跳超时秒数 */
    @Value("${devicewatch.offline-timeout-seconds:90}")
    private long offlineTimeoutSeconds;
    /** 升级检查用的分钟数（0=关闭升级） */
    @Value("${devicewatch.escalate-check-minutes:30}")
    private int escalateCheckMinutes;

    public MonitorTasks(DeviceMapper deviceMapper, AlertService alertService,
                       AlertMapper alertMapper, TelemetryMapper telemetryMapper) {
        this.deviceMapper = deviceMapper;
        this.alertService = alertService;
        this.alertMapper = alertMapper;
        this.telemetryMapper = telemetryMapper;
    }

    @Scheduled(fixedRate = 30000)
    public void offlineCheck() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(offlineTimeoutSeconds);
        List<Device> silent = deviceMapper.findSilentSince(before);
        for (Device d : silent) {
            // 从未上报过心跳（刚创建）的设备不判离线
            if (d.getLastSeenAt() == null) continue;
            alertService.fireOffline(d);
        }
        // 心跳恢复的设备：关闭离线告警
        List<Device> offline = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().eq(Device::getStatus, "OFFLINE"));
        for (Device d : offline) {
            if (d.getLastSeenAt() != null && d.getLastSeenAt().isAfter(before)) {
                alertService.resolveOffline(d.getId());
                d.setStatus("ONLINE");
                deviceMapper.updateById(d);
                alertService.refreshDeviceStatus(d.getId());
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void escalationCheck() {
        if (escalateCheckMinutes > 0) {
            alertService.escalateDue(escalateCheckMinutes);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanTelemetry() {
        LocalDateTime before = LocalDateTime.now().minusDays(7);
        telemetryMapper.delete(new LambdaQueryWrapper<com.devicewatch.ingest.TelemetryRecord>()
                .lt(com.devicewatch.ingest.TelemetryRecord::getTs, before));
    }
}
