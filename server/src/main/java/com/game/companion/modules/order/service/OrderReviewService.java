package com.game.companion.modules.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.game.companion.modules.order.dto.CreateReviewDTO;
import com.game.companion.modules.order.entity.OrderReview;

public interface OrderReviewService extends IService<OrderReview> {

    /**
     * 客户提交订单服务好评
     *
     * @param clientUserId 评价客户ID
     * @param dto          评价数据
     * @return 评价记录ID
     */
    Long createReview(Long clientUserId, CreateReviewDTO dto);

    /**
     * 根据订单号查询评价详情
     *
     * @param orderSn 订单号
     * @return 评价信息
     */
    OrderReview getReviewByOrderSn(String orderSn);

    /**
     * 分页查询陪玩师收到的评价列表
     *
     * @param companionUserId 陪练师ID
     * @param pageNum         页码
     * @param pageSize        页大小
     * @return 分页结果
     */
    Page<OrderReview> getCompanionReviews(Long companionUserId, int pageNum, int pageSize);
}
