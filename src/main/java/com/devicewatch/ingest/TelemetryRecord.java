package com.devicewatch.ingest;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("telemetry")
public class TelemetryRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String metricCode;
    private Double metricValue;
    private LocalDateTime ts;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }
    public LocalDateTime getTs() { return ts; }
    public void setTs(LocalDateTime ts) { this.ts = ts; }
}
