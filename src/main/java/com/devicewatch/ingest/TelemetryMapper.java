package com.devicewatch.ingest;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TelemetryMapper extends BaseMapper<TelemetryRecord> {

    @Select("SELECT * FROM telemetry WHERE device_id = #{deviceId} AND metric_code = #{metricCode} "
            + "AND ts >= #{from} ORDER BY ts ASC")
    List<TelemetryRecord> history(@Param("deviceId") Long deviceId,
                                 @Param("metricCode") String metricCode,
                                 @Param("from") LocalDateTime from);

    @Select("SELECT DISTINCT metric_code FROM telemetry WHERE device_id = #{deviceId}")
    List<String> metricsOfDevice(Long deviceId);

    /** 某设备近期遥测（时间倒序），快照接口用：一次查询取全量，Java 侧按测点取最新 */
    @Select("SELECT * FROM telemetry WHERE device_id = #{deviceId} AND ts >= #{from} ORDER BY ts DESC")
    List<TelemetryRecord> recentByDevice(@Param("deviceId") Long deviceId,
                                        @Param("from") LocalDateTime from);
}
