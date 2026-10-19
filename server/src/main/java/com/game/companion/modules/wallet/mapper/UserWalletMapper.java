package com.game.companion.modules.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.wallet.entity.UserWallet;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserWalletMapper extends BaseMapper<UserWallet> {
}
