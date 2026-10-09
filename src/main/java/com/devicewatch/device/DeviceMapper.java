package com.devicewatch.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
    @Select("SELECT * FROM device WHERE code = #{code} LIMIT 1")
    Device findByCode(String code);

    @Update("UPDATE device SET last_seen_at = #{ts}, status = #{status} WHERE id = #{id}")
    int touchStatus(@Param("id") Long id, @Param("ts") LocalDateTime ts, @Param("status") String status);

    /** 心跳：只刷新时间，不碰状态列 */
    @Update("UPDATE device SET last_seen_at = #{ts} WHERE id = #{id}")
    int touchSeen(@Param("id") Long id, @Param("ts") LocalDateTime ts);

    @Select("SELECT * FROM device WHERE last_seen_at < #{before} AND status <> 'OFFLINE'")
    List<Device> findSilentSince(LocalDateTime before);
}
