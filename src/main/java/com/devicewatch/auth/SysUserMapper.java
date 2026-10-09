package com.devicewatch.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser findByUsername(String username);
}
