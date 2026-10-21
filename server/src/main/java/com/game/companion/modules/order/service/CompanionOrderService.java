package com.game.companion.modules.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.game.companion.modules.order.dto.CreateOrderDTO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.payment.dto.PaymentResultVO;

public interface CompanionOrderService extends IService<CompanionOrder> {

    /**
     * 客户下单并拉起支付 (支持 Mock / 微信)
     */
    PaymentResultVO createAndPayOrder(Long clientUserId, CreateOrderDTO dto);

    /**
     * 模拟支付成功回调 (用于开发期测试一键完成支付) —— 幂等，重复调用不会重复落支付流水
     */
    void mockPaySuccess(String orderSn);

    /**
     * 【支付事实落盘】记录一笔真实支付（微信V3回调 / Mock 支付）并把订单流转为「待接单」。
     * 幂等：同一订单重复回调不会重复落 pay_flow，也不会重复触发派单通知。
     *
     * @param payType       支付渠道标识 (mock / wechat_pay)
     * @param transactionId 第三方支付机构流水号（微信商户交易单号）
     * @param payAmount     实际支付金额（分）
     */
    void recordPaySuccess(String orderSn, String payType, String transactionId, Integer payAmount);

    /**
     * 陪玩师接单 (指定订单 / 派单大厅抢单)
     */
    void acceptOrder(Long companionUserId, String orderSn);

    /**
     * 陪玩师申请结单 (上传战绩凭证)
     */
    void applyFinish(Long companionUserId, String orderSn, String screenshotUrl);

    /**
     * 客户确认完成订单 (解冻佣金划入陪玩师钱包)
     */
    void confirmFinish(Long clientUserId, String orderSn);

    /**
     * 【系统自动结单】客户超时未确认时由定时任务自动完结并清算，仅供内部调度使用
     */
    void autoConfirmFinish(String orderSn);

    /**
     * 根据订单号从数据库查询真实订单详情
     */
    CompanionOrder getOrderDetail(String orderSn);

    /**
     * 分页查询我的订单列表 (按下单客户 client 或 陪玩师 companion 查询)
     */
    Page<CompanionOrder> getMyOrders(Long userId, String roleType, Integer orderStatus, int pageNum, int pageSize);

    /**
     * 客户撤回/取消待接单的订单并全额退款
     */
    void cancelAndRefundOrder(Long clientUserId, String orderSn);

    /**
     * 开黑服务中一键加钟/续费 (保留作为架构扩展切入点)
     */
    PaymentResultVO addOnOrder(Long clientUserId, String orderSn, int addQuantity);

    /**
     * 校验并自动触发接单超时退款 (超时未接单全额原路退款)
     */
    boolean checkAndRefundIfTimeout(String orderSn);

    /**
     * 客户发起售后维权与平台争议仲裁
     */
    void applyDispute(Long clientUserId, String orderSn, String reason);

    /**
     * 管理员仲裁处理售后维权
     */
    void auditDispute(Long adminUserId, String orderSn, boolean refundToClient, String remark);

    /**
     * 校验当前登录用户是否有权查看该订单详情 (下单客户 / 接单陪玩师 / 平台管理员)
     */
    boolean canViewOrder(Long viewerUserId, int viewerRole, CompanionOrder order);
}
