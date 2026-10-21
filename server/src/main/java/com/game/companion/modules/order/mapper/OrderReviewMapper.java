package com.game.companion.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.order.entity.OrderReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface OrderReviewMapper extends BaseMapper<OrderReview> {

    @Select("SELECT IFNULL(AVG(score), 5.0) FROM order_review WHERE companion_user_id = #{companionUserId}")
    BigDecimal calculateAverageRating(@Param("companionUserId") Long companionUserId);
}
