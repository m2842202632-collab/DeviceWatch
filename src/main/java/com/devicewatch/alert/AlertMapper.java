package com.devicewatch.alert;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertMapper extends BaseMapper<AlertRecord> {

    @Select("SELECT * FROM alert_record WHERE rule_id = #{ruleId} AND device_id = #{deviceId} "
            + "AND status IN ('FIRING','ACKED') ORDER BY id DESC LIMIT 1")
    AlertRecord findActive(@Param("ruleId") Long ruleId, @Param("deviceId") Long deviceId);

    @Select("SELECT COUNT(*) FROM alert_record WHERE device_id = #{deviceId} AND status = 'FIRING'")
    int countFiringByDevice(Long deviceId);

    /** 静默期抑制：只累加计数，不新增记录、不推送 */
    @Update("UPDATE alert_record SET suppressed_count = suppressed_count + 1, last_fired_at = #{ts} "
            + "WHERE rule_id = #{ruleId} AND device_id = #{deviceId} AND status IN ('FIRING','ACKED')")
    int incrementSuppressed(@Param("ruleId") Long ruleId, @Param("deviceId") Long deviceId,
                            @Param("ts") LocalDateTime ts);

    /** 重复触发：刷新峰值与次数 */
    @Update("UPDATE alert_record SET fire_count = fire_count + 1, "
            + "max_value = CASE WHEN #{value} > max_value THEN #{value} ELSE max_value END, "
            + "last_fired_at = #{ts} "
            + "WHERE rule_id = #{ruleId} AND device_id = #{deviceId} AND status IN ('FIRING','ACKED')")
    int refreshOnFire(@Param("ruleId") Long ruleId, @Param("deviceId") Long deviceId,
                      @Param("value") Double value, @Param("ts") LocalDateTime ts);

    /** 待升级：WARN 且未确认且超过升级时间 */
    @Select("SELECT * FROM alert_record WHERE status = 'FIRING' AND level = 'WARN' "
            + "AND first_fired_at < #{before}")
    List<AlertRecord> findEscalationDue(@Param("before") LocalDateTime before);
}
