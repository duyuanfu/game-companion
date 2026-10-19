package com.game.companion.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.order.entity.CompanionOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CompanionOrderMapper extends BaseMapper<CompanionOrder> {

    /**
     * 乐观锁抢单更新：仅当订单处于待接单(1)且原陪玩师为空或流转大厅标记为1时才允许抢单成功
     */
    @Update("UPDATE companion_order SET companion_user_id = #{companionUserId}, order_status = 2, accept_time = NOW() " +
            "WHERE order_sn = #{orderSn} AND order_status = 1 AND (companion_user_id IS NULL OR is_transferred = 1)")
    int grabOrderAtomically(@Param("orderSn") String orderSn, @Param("companionUserId") Long companionUserId);
}
