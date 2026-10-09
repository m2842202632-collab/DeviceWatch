package com.devicewatch.alert;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 告警记录。状态机：FIRING(告警中) -> ACKED(已确认) -> RESOLVED(已恢复/已关闭)。
 * 同一规则+设备在告警中只保留一条，重复触发走 fireCount / suppressedCount 累加。
 */
@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 规则 id；-1 表示系统离线告警 */
    private Long ruleId;
    private Long deviceId;
    private String metricCode;
    /** WARN / CRITICAL */
    private String level;
    /** FIRING / ACKED / RESOLVED */
    private String status;
    private Double triggerValue;
    private Double maxValue;
    private Integer fireCount;
    /** 静默期内被抑制的次数（告警风暴抑制的证据） */
    private Integer suppressedCount;
    private LocalDateTime firstFiredAt;
    private LocalDateTime lastFiredAt;
    private String ackBy;
    private LocalDateTime ackAt;
    private LocalDateTime resolvedAt;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getTriggerValue() { return triggerValue; }
    public void setTriggerValue(Double triggerValue) { this.triggerValue = triggerValue; }
    public Double getMaxValue() { return maxValue; }
    public void setMaxValue(Double maxValue) { this.maxValue = maxValue; }
    public Integer getFireCount() { return fireCount; }
    public void setFireCount(Integer fireCount) { this.fireCount = fireCount; }
    public Integer getSuppressedCount() { return suppressedCount; }
    public void setSuppressedCount(Integer suppressedCount) { this.suppressedCount = suppressedCount; }
    public LocalDateTime getFirstFiredAt() { return firstFiredAt; }
    public void setFirstFiredAt(LocalDateTime firstFiredAt) { this.firstFiredAt = firstFiredAt; }
    public LocalDateTime getLastFiredAt() { return lastFiredAt; }
    public void setLastFiredAt(LocalDateTime lastFiredAt) { this.lastFiredAt = lastFiredAt; }
    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public LocalDateTime getAckAt() { return ackAt; }
    public void setAckAt(LocalDateTime ackAt) { this.ackAt = ackAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
