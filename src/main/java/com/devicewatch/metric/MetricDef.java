package com.devicewatch.metric;

import java.util.*;

/**
 * 测点字典：每种设备类型有哪些测点、单位、正常范围。
 * 模拟器按此生成数据，规则引擎按 metricCode 匹配规则。
 */
public class MetricDef {
    public final String code;
    public final String name;
    public final String unit;
    /** 模拟基线值 */
    public final double base;
    /** 正常波动幅度 */
    public final double noise;

    public MetricDef(String code, String name, String unit, double base, double noise) {
        this.code = code; this.name = name; this.unit = unit;
        this.base = base; this.noise = noise;
    }

    /** 设备类型 -> 测点列表 */
    private static final Map<String, List<MetricDef>> BY_TYPE = new LinkedHashMap<>();
    /** 设备类型中文名 */
    private static final Map<String, String> TYPE_NAMES = new LinkedHashMap<>();

    static {
        TYPE_NAMES.put("PUMP", "离心泵");
        TYPE_NAMES.put("FAN", "引风机");
        TYPE_NAMES.put("COMPRESSOR", "空压机");
        TYPE_NAMES.put("CONVEYOR", "皮带输送机");

        BY_TYPE.put("PUMP", Arrays.asList(
                new MetricDef("temperature", "轴承温度", "°C", 62, 3),
                new MetricDef("vibration", "振动速度", "mm/s", 2.8, 0.5),
                new MetricDef("pressure", "出口压力", "MPa", 0.55, 0.05),
                new MetricDef("current", "电机电流", "A", 42, 3)));
        BY_TYPE.put("FAN", Arrays.asList(
                new MetricDef("temperature", "轴承温度", "°C", 58, 3),
                new MetricDef("vibration", "振动速度", "mm/s", 3.5, 0.6),
                new MetricDef("speed", "转速", "rpm", 1450, 30)));
        BY_TYPE.put("COMPRESSOR", Arrays.asList(
                new MetricDef("temperature", "排气温度", "°C", 78, 4),
                new MetricDef("pressure", "排气压力", "MPa", 0.75, 0.04),
                new MetricDef("current", "电机电流", "A", 68, 4)));
        BY_TYPE.put("CONVEYOR", Arrays.asList(
                new MetricDef("temperature", "电机温度", "°C", 55, 3),
                new MetricDef("current", "电机电流", "A", 28, 2),
                new MetricDef("speed", "带速", "m/s", 2.5, 0.15)));
    }

    public static List<MetricDef> ofType(String type) {
        return BY_TYPE.getOrDefault(type, Collections.emptyList());
    }

    public static Set<String> types() { return BY_TYPE.keySet(); }

    public static String typeName(String type) {
        return TYPE_NAMES.getOrDefault(type, type);
    }

    public static Map<String, String> typeNames() { return TYPE_NAMES; }

    public static MetricDef find(String type, String code) {
        return ofType(type).stream().filter(m -> m.code.equals(code)).findFirst().orElse(null);
    }
}
