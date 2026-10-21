package com.game.companion.modules.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.companion.common.enums.OrderStatusEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import com.game.companion.modules.order.dto.CreateReviewDTO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.entity.OrderReview;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.order.mapper.OrderReviewMapper;
import com.game.companion.modules.order.service.OrderReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewServiceImpl extends ServiceImpl<OrderReviewMapper, OrderReview> implements OrderReviewService {

    private final CompanionOrderMapper companionOrderMapper;
    private final CompanionProfileMapper companionProfileMapper;
    private final OrderReviewMapper orderReviewMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReview(Long clientUserId, CreateReviewDTO dto) {
        CompanionOrder order = companionOrderMapper.selectOne(
                new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, dto.getOrderSn())
        );
        if (order == null) {
            throw new ApiException("未找到该订单: " + dto.getOrderSn());
        }
        if (!clientUserId.equals(order.getClientUserId())) {
            throw new ApiException("操作被拒绝：只有下单客户本人才能进行服务评价");
        }
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.FINISHED.getCode()) {
            throw new ApiException("订单尚未圆满完结，暂时无法提交评价");
        }

        // 防重复评价校验
        Long existingCount = count(new LambdaQueryWrapper<OrderReview>().eq(OrderReview::getOrderId, order.getId()));
        if (existingCount > 0) {
            throw new ApiException("该订单已提交过评价，请勿重复操作！");
        }

        OrderReview review = new OrderReview();
        review.setOrderId(order.getId());
        review.setOrderSn(order.getOrderSn());
        review.setClientUserId(clientUserId);
        review.setCompanionUserId(order.getCompanionUserId());
        review.setScore(dto.getScore());
        review.setSkillScore(dto.getSkillScore() != null ? dto.getSkillScore() : dto.getScore());
        review.setAttitudeScore(dto.getAttitudeScore() != null ? dto.getAttitudeScore() : dto.getScore());
        review.setTags(dto.getTags());
        review.setContent(dto.getContent());
        review.setCreateTime(LocalDateTime.now());
        save(review);

        // 联动更新陪练师个人主档的综合评分
        if (order.getCompanionUserId() != null) {
            BigDecimal avgRating = orderReviewMapper.calculateAverageRating(order.getCompanionUserId());
            if (avgRating != null) {
                BigDecimal roundedRating = avgRating.setScale(1, RoundingMode.HALF_UP);
                CompanionProfile profile = companionProfileMapper.selectOne(
                        new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, order.getCompanionUserId())
                );
                if (profile != null) {
                    profile.setRating(roundedRating);
                    profile.setUpdateTime(LocalDateTime.now());
                    companionProfileMapper.updateById(profile);
                    log.info("【好评联动生效】陪练师[{}]收到新评价({}星)，最新综合均分更新为: {}",
                            order.getCompanionUserId(), dto.getScore(), roundedRating);
                }
            }
        }

        return review.getId();
    }

    @Override
    public OrderReview getReviewByOrderSn(String orderSn) {
        return getOne(new LambdaQueryWrapper<OrderReview>().eq(OrderReview::getOrderSn, orderSn));
    }

    @Override
    public Page<OrderReview> getCompanionReviews(Long companionUserId, int pageNum, int pageSize) {
        Page<OrderReview> page = new Page<>(pageNum, pageSize);
        return page(page, new LambdaQueryWrapper<OrderReview>()
                .eq(OrderReview::getCompanionUserId, companionUserId)
                .orderByDesc(OrderReview::getCreateTime));
    }
}
