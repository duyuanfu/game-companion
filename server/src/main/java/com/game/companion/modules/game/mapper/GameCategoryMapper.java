package com.game.companion.modules.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.game.entity.GameCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameCategoryMapper extends BaseMapper<GameCategory> {
}
