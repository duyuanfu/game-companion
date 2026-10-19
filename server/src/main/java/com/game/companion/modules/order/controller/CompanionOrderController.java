package com.game.companion.modules.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.common.api.CommonResult;
import com.game.companion.modules.order.dto.CreateOrderDTO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.service.CompanionOrderService;
import com.game.companion.payment.dto.PaymentResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Tag(name = "订单全链路接口", description = "包含下单支付、接单、大厅抢单、流转、结单确认")
public class CompanionOrderController {

    private final CompanionOrderService companionOrderService;

    @PostMapping("/create")
    @Operation(summary = "客户创建订单并拉起支付 (支持Mock一键支付/微信)")
    public CommonResult<PaymentResultVO> createOrder(@Valid @RequestBody CreateOrderDTO dto) {
        // 从当前登录会话中获取真实用户ID，未登录则兜底 10000L
        Long currentUserId = cn.dev33.satoken.stp.StpUtil.isLogin() 
                ? cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong() 
                : 10000L;
        PaymentResultVO paymentResult = companionOrderService.createAndPayOrder(currentUserId, dto);
        return CommonResult.success(paymentResult);
    }

    @GetMapping("/detail/{orderSn}")
    @Operation(summary = "从数据库查询真实订单详情")
    public CommonResult<CompanionOrder> getOrderDetail(@PathVariable String orderSn) {
        return CommonResult.success(companionOrderService.getOrderDetail(orderSn));
    }

    @GetMapping("/my-list")
    @Operation(summary = "查询我的订单列表 (支持按全部/待接单/服务中/待确认/已完成筛选)")
    public CommonResult<Page<CompanionOrder>> getMyOrders(@RequestParam(defaultValue = "client") String roleType,
                                                          @RequestParam(required = false) Integer orderStatus,
                                                          @RequestParam(required = false) Long userId,
                                                          @RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = userId;
        if (currentUserId == null || currentUserId <= 0) {
            currentUserId = cn.dev33.satoken.stp.StpUtil.isLogin() 
                    ? cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong() 
                    : 10000L;
        }
        Page<CompanionOrder> page = companionOrderService.getMyOrders(currentUserId, roleType, orderStatus, pageNum, pageSize);
        return CommonResult.success(page);
    }

    @PostMapping("/mock-pay/{orderSn}")
    @Operation(summary = "【测试专用】手动触发模拟支付成功回调")
    public CommonResult<String> mockPay(@PathVariable String orderSn) {
        companionOrderService.mockPaySuccess(orderSn);
        return CommonResult.success("模拟支付成功，订单流转为待接单并已触发派单提醒");
    }

    @PostMapping("/accept/{orderSn}")
    @Operation(summary = "陪玩师指定接单 (进入服务中)")
    public CommonResult<String> acceptOrder(@PathVariable String orderSn, @RequestParam Long companionUserId) {
        companionOrderService.acceptOrder(companionUserId, orderSn);
        return CommonResult.success("接单成功，双方可开始IM沟通");
    }

    @PostMapping("/grab/{orderSn}")
    @Operation(summary = "派单大厅并发抢单 (带分布式锁防超卖)")
    public CommonResult<String> grabOrder(@PathVariable String orderSn, @RequestParam Long companionUserId) {
        companionOrderService.grabOrderFromHall(companionUserId, orderSn);
        return CommonResult.success("恭喜抢单成功！");
    }

    @PostMapping("/transfer/{orderSn}")
    @Operation(summary = "模拟超时自动流转至派单大厅")
    public CommonResult<String> transferToHall(@PathVariable String orderSn) {
        companionOrderService.transferToPublicHall(orderSn);
        return CommonResult.success("订单已流转至公共抢单大厅");
    }

    @PostMapping("/apply-finish/{orderSn}")
    @Operation(summary = "陪玩师申请完结订单 (上传战绩凭证)")
    public CommonResult<String> applyFinish(@PathVariable String orderSn,
                                            @RequestParam Long companionUserId,
                                            @RequestParam String screenshotUrl) {
        companionOrderService.applyFinish(companionUserId, orderSn, screenshotUrl);
        return CommonResult.success("完结申请已提交，等待客户确认");
    }

    @PostMapping("/confirm/{orderSn}")
    @Operation(summary = "客户确认结单 (收益划扣到陪玩师钱包)")
    public CommonResult<String> confirmOrder(@PathVariable String orderSn, @RequestParam Long clientUserId) {
        companionOrderService.confirmFinish(clientUserId, orderSn);
        return CommonResult.success("订单完结，佣金已划入陪玩师钱包");
    }

    @PostMapping("/cancel/{orderSn}")
    @Operation(summary = "客户撤回/取消待接单订单 (全额原路退款)")
    public CommonResult<String> cancelOrder(@PathVariable String orderSn, @RequestParam(required = false) Long clientUserId) {
        Long targetClientId = clientUserId;
        if (targetClientId == null || targetClientId <= 0) {
            targetClientId = cn.dev33.satoken.stp.StpUtil.isLogin() 
                    ? cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong() 
                    : 10000L;
        }
        companionOrderService.cancelAndRefundOrder(targetClientId, orderSn);
        return CommonResult.success("订单已成功撤回并原路全额退款");
    }

    @PostMapping("/add-on/{orderSn}")
    @Operation(summary = "【一键加钟】开黑服务中一键续费加钟 (+1小时/+1局)")
    public CommonResult<PaymentResultVO> addOnOrder(@PathVariable String orderSn,
                                                    @RequestParam(defaultValue = "1") int quantity,
                                                    @RequestParam(required = false) Long clientUserId) {
        Long targetClientId = clientUserId;
        if (targetClientId == null || targetClientId <= 0) {
            targetClientId = cn.dev33.satoken.stp.StpUtil.isLogin() 
                    ? cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong() 
                    : 10000L;
        }
        PaymentResultVO result = companionOrderService.addOnOrder(targetClientId, orderSn, quantity);
        return CommonResult.success(result, "加钟续费成功！已通知开黑房间与陪练师");
    }

    @PostMapping("/auto-refund-timeout/{orderSn}")
    @Operation(summary = "【超时退款】接单10分钟超时触发自动原路全额退款")
    public CommonResult<String> autoRefundTimeout(@PathVariable String orderSn) {
        boolean refunded = companionOrderService.checkAndRefundIfTimeout(orderSn);
        return CommonResult.success(refunded ? "接单超时自动退款成功" : "无需超时退款或非待接单状态");
    }

    @GetMapping("/hall/list")
    @Operation(summary = "查看派单大厅抢单池")
    public CommonResult<Page<CompanionOrder>> getHallList(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        Page<CompanionOrder> page = companionOrderService.getHallOrders(pageNum, pageSize);
        return CommonResult.success(page);
    }

    @GetMapping("/debug/list")
    @Operation(summary = "【诊断】查询数据库中所有订单记录与字段")
    public CommonResult<java.util.List<CompanionOrder>> debugAllOrders() {
        return CommonResult.success(companionOrderService.list());
    }
}
