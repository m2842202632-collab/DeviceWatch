package com.devicewatch.rule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {
    @Select("SELECT * FROM alert_rule WHERE enabled = 1 ORDER BY id")
    List<AlertRule> findEnabled();
}
