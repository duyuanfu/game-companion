package com.game.companion.modules.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.common.api.CommonResult;
import com.game.companion.common.api.ResultCode;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.common.security.AuthGuard;
import com.game.companion.common.security.DevToolGuard;
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
    private final AuthGuard authGuard;
    private final DevToolGuard devToolGuard;

    @PostMapping("/create")
    @Operation(summary = "客户创建订单并拉起支付 (支持Mock一键支付/微信)")
    public CommonResult<PaymentResultVO> createOrder(@Valid @RequestBody CreateOrderDTO dto) {
        // 身份一律取自当前登录会话，杜绝客户端伪造下单人
        Long currentUserId = authGuard.currentUserId();
        PaymentResultVO paymentResult = companionOrderService.createAndPayOrder(currentUserId, dto);
        return CommonResult.success(paymentResult);
    }

    @GetMapping("/detail/{orderSn}")
    @Operation(summary = "从数据库查询真实订单详情 (仅限订单相关方或管理员)")
    public CommonResult<CompanionOrder> getOrderDetail(@PathVariable String orderSn) {
        Long currentUserId = authGuard.currentUserId();
        int currentRole = authGuard.currentRole();
        CompanionOrder order = companionOrderService.getOrderDetail(orderSn);
        if (!companionOrderService.canViewOrder(currentUserId, currentRole, order)) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        // 按查看者身份裁剪平台内部财务数据（客户不可见清算明细）
        return CommonResult.success(
                companionOrderService.getOrderDetailForViewer(orderSn, currentUserId, currentRole));
    }

    @GetMapping("/my-list")
    @Operation(summary = "查询我的订单列表 (支持按全部/待接单/服务中/待确认/已完成筛选)")
    public CommonResult<Page<CompanionOrder>> getMyOrders(@RequestParam(defaultValue = "client") String roleType,
                                                          @RequestParam(required = false) Integer orderStatus,
                                                          @RequestParam(required = false) Long userId,
                                                          @RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize) {
        // userId 参数已废弃并被强制忽略：只能查询当前登录用户自己的订单
        Long currentUserId = authGuard.currentUserId();
        Page<CompanionOrder> page = companionOrderService.getMyOrders(currentUserId, roleType, orderStatus, pageNum, pageSize);
        return CommonResult.success(page);
    }

    @PostMapping("/pay/{orderSn}")
    @Operation(summary = "【发起支付】对待付款订单拉起微信支付 (Mock 模式立即模拟支付成功)")
    public CommonResult<com.game.companion.payment.dto.PaymentResultVO> payOrder(@PathVariable String orderSn) {
        Long clientUserId = authGuard.currentUserId();
        return CommonResult.success(companionOrderService.payOrder(clientUserId, orderSn));
    }

    @PostMapping("/mock-pay/{orderSn}")
    @Operation(summary = "【开发调试专用】手动触发模拟支付成功回调")
    public CommonResult<String> mockPay(@PathVariable String orderSn) {
        devToolGuard.requireEnabled();
        companionOrderService.mockPaySuccess(orderSn);
        return CommonResult.success("模拟支付成功，订单流转为待接单并已触发派单提醒");
    }

    @PostMapping("/accept/{orderSn}")
    @Operation(summary = "陪玩师接单 (进入服务中)")
    public CommonResult<String> acceptOrder(@PathVariable String orderSn) {
        // 接单人只能是当前登录的陪玩师本人
        Long companionUserId = authGuard.currentUserId();
        companionOrderService.acceptOrder(companionUserId, orderSn);
        return CommonResult.success("接单成功，双方可开始IM沟通");
    }

    @PostMapping("/apply-finish/{orderSn}")
    @Operation(summary = "陪玩师申请结单订单 (上传战绩凭证)")
    public CommonResult<String> applyFinish(@PathVariable String orderSn,
                                            @RequestParam String screenshotUrl) {
        Long companionUserId = authGuard.currentUserId();
        companionOrderService.applyFinish(companionUserId, orderSn, screenshotUrl);
        return CommonResult.success("完结申请已提交，等待客户确认");
    }

    @PostMapping("/confirm/{orderSn}")
    @Operation(summary = "客户确认结单 (收益划扣到陪玩师钱包)")
    public CommonResult<String> confirmOrder(@PathVariable String orderSn) {
        // 确认人只能是下单客户本人
        Long clientUserId = authGuard.currentUserId();
        companionOrderService.confirmFinish(clientUserId, orderSn);
        return CommonResult.success("订单完结，佣金已划入陪玩师钱包");
    }

    @PostMapping("/cancel/{orderSn}")
    @Operation(summary = "客户撤回/取消待接单订单 (全额原路退款)")
    public CommonResult<String> cancelOrder(@PathVariable String orderSn) {
        Long clientUserId = authGuard.currentUserId();
        companionOrderService.cancelAndRefundOrder(clientUserId, orderSn);
        return CommonResult.success("订单已成功撤回并原路全额退款");
    }

    @PostMapping("/add-on/{orderSn}")
    @Operation(summary = "【一键加钟】开黑服务中一键续费加钟 (+1小时/+1局)")
    public CommonResult<PaymentResultVO> addOnOrder(@PathVariable String orderSn,
                                                    @RequestParam(defaultValue = "1") int quantity) {
        Long clientUserId = authGuard.currentUserId();
        PaymentResultVO result = companionOrderService.addOnOrder(clientUserId, orderSn, quantity);
        return CommonResult.success(result, "加钟续费成功！已通知开黑房间与陪练师");
    }

    @PostMapping("/auto-refund-timeout/{orderSn}")
    @Operation(summary = "【超时退款】接单超时触发自动原路全额退款 (系统调度已自动执行，此接口供人工兜底)")
    public CommonResult<String> autoRefundTimeout(@PathVariable String orderSn) {
        Long currentUserId = authGuard.currentUserId();
        int currentRole = authGuard.currentRole();
        CompanionOrder order = companionOrderService.getOrderDetail(orderSn);
        boolean isOwner = currentUserId.equals(order.getClientUserId());
        if (!isOwner && currentRole != RoleEnum.ADMIN.getCode()) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        boolean refunded = companionOrderService.checkAndRefundIfTimeout(orderSn);
        return CommonResult.success(refunded ? "接单超时自动退款成功" : "无需超时退款或非待接单状态");
    }

    @PostMapping("/dispute/apply/{orderSn}")
    @Operation(summary = "【售后维权】客户发起维权争议申请")
    public CommonResult<String> applyDispute(@PathVariable String orderSn,
                                             @RequestParam(required = false) String reason) {
        Long clientUserId = authGuard.currentUserId();
        companionOrderService.applyDispute(clientUserId, orderSn, reason);
        return CommonResult.success("维权申请已提交，平台管理员将介入仲裁！");
    }

    @PostMapping("/dispute/audit/{orderSn}")
    @Operation(summary = "【平台仲裁】管理员对维权订单进行裁决")
    public CommonResult<String> auditDispute(@PathVariable String orderSn,
                                             @RequestParam boolean refundToClient,
                                             @RequestParam(defaultValue = "平台仲裁完成") String remark) {
        // 仲裁属于平台特权操作，强制管理员角色
        authGuard.requireAdmin();
        Long adminUserId = authGuard.currentUserId();
        companionOrderService.auditDispute(adminUserId, orderSn, refundToClient, remark);
        return CommonResult.success("仲裁处理成功！");
    }

    @GetMapping("/debug/list")
    @Operation(summary = "【诊断】查询数据库中所有订单记录与字段 (仅开发环境)")
    public CommonResult<java.util.List<CompanionOrder>> debugAllOrders() {
        devToolGuard.requireEnabled();
        authGuard.requireAdmin();
        return CommonResult.success(companionOrderService.list());
    }
}
