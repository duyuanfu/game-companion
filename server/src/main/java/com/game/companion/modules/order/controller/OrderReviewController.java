package com.game.companion.modules.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.common.api.CommonResult;
import com.game.companion.common.security.AuthGuard;
import com.game.companion.modules.order.dto.CreateReviewDTO;
import com.game.companion.modules.order.entity.OrderReview;
import com.game.companion.modules.order.service.OrderReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单评价接口")
@RestController
@RequestMapping("/order/review")
@RequiredArgsConstructor
public class OrderReviewController {

    private final OrderReviewService orderReviewService;
    private final AuthGuard authGuard;

    @Operation(summary = "提交订单服务评价")
    @PostMapping("/submit")
    public CommonResult<Long> submitReview(@Valid @RequestBody CreateReviewDTO dto) {
        // 评价人只能是当前登录的下单客户本人
        Long clientUserId = authGuard.currentUserId();
        Long reviewId = orderReviewService.createReview(clientUserId, dto);
        return CommonResult.success(reviewId, "评价提交成功，好评已计入陪练师综合评分！");
    }

    @Operation(summary = "根据订单号查询评价详情")
    @GetMapping("/detail/{orderSn}")
    public CommonResult<OrderReview> getReviewDetail(@PathVariable String orderSn) {
        return CommonResult.success(orderReviewService.getReviewByOrderSn(orderSn));
    }

    @Operation(summary = "分页查询陪玩师收到的评价列表")
    @GetMapping("/companion/{companionUserId}")
    public CommonResult<Page<OrderReview>> getCompanionReviews(@PathVariable Long companionUserId,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return CommonResult.success(orderReviewService.getCompanionReviews(companionUserId, pageNum, pageSize));
    }
}
