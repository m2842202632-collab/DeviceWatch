package com.devicewatch.ingest;

/** Kafka 消息体 / 模拟器共用：单测点遥测 */
public class Telemetry {
    private String deviceId;   // 设备编码，如 PUMP-01
    private String metric;     // 测点编码，如 temperature
    private double value;
    private long ts;           // epoch millis

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
}
