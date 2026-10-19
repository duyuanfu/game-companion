package com.game.companion.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
