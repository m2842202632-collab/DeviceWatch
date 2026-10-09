package com.devicewatch.rule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 告警规则。deviceId 为空表示应用到全部设备。
 * durationTimes：连续超标 N 次才触发（防抖）；silenceMinutes：静默期（防风暴）；
 * escalateMinutes：未确认自动升级。
 */
@TableName("alert_rule")
public class AlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long deviceId;
    private String metricCode;
    /** GT 大于 / LT 小于 */
    private String operator;
    private Double threshold;
    private Integer durationTimes;
    /** WARN / CRITICAL */
    private String level;
    private Integer silenceMinutes;
    private Integer escalateMinutes;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public Integer getDurationTimes() { return durationTimes; }
    public void setDurationTimes(Integer durationTimes) { this.durationTimes = durationTimes; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Integer getSilenceMinutes() { return silenceMinutes; }
    public void setSilenceMinutes(Integer silenceMinutes) { this.silenceMinutes = silenceMinutes; }
    public Integer getEscalateMinutes() { return escalateMinutes; }
    public void setEscalateMinutes(Integer escalateMinutes) { this.escalateMinutes = escalateMinutes; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
