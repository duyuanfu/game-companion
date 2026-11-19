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
     * 带客户端 IP 的下单支付（H5 支付需要 scene_info.payer_client_ip）。
     */
    PaymentResultVO createAndPayOrder(Long clientUserId, CreateOrderDTO dto, String clientIp);

    /**
     * 模拟支付成功回调 (用于开发期测试一键完成支付) —— 幂等，重复调用不会重复落支付流水
     */
    void mockPaySuccess(String orderSn);

    /**
     * 【发起支付】对处于「待付款(0)」的订单发起支付，返回前端拉起微信支付所需参数。
     * Mock 模式下会立即模拟支付成功并流转为「待接单(1)」。
     */
    PaymentResultVO payOrder(Long clientUserId, String orderSn);

    /**
     * 带支付场景与客户端 IP 的支付发起（Web Native / H5）。
     */
    PaymentResultVO payOrder(Long clientUserId, String orderSn, String payScene, String clientIp);

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
     * 【带查看者身份】查询订单详情。
     *
     * <p>平台内部财务数据（毛抽佣 / 微信通道费 / 平台净利）属于商业机密，
     * 仅陪玩师本人与平台管理员可见；普通客户拿到的订单详情中不包含清算单数据。</p>
     */
    CompanionOrder getOrderDetailForViewer(String orderSn, Long viewerUserId, int viewerRole);

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
     * 【履约超时自动退款】陪玩师接单后超过阈值仍未完成服务，系统自动为客户全额原路退款。
     */
    boolean checkAndRefundIfServiceTimeout(String orderSn);

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
