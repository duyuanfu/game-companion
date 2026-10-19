package com.game.companion.modules.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.wallet.entity.WithdrawRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WithdrawRecordMapper extends BaseMapper<WithdrawRecord> {
}
