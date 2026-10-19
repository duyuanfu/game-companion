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
     * 模拟支付成功回调 (用于开发期测试一键完成支付)
     */
    void mockPaySuccess(String orderSn);

    /**
     * 陪玩师接单 (指定订单)
     */
    void acceptOrder(Long companionUserId, String orderSn);

    /**
     * 派单大厅抢单 (带 Redisson 分布式锁防超卖)
     */
    void grabOrderFromHall(Long companionUserId, String orderSn);

    /**
     * 订单超时自动流转至派单大厅 (延时任务触发)
     */
    void transferToPublicHall(String orderSn);

    /**
     * 陪玩师申请结单 (上传战绩凭证)
     */
    void applyFinish(Long companionUserId, String orderSn, String screenshotUrl);

    /**
     * 客户确认完成订单 (解冻佣金划入陪玩师钱包)
     */
    void confirmFinish(Long clientUserId, String orderSn);

    /**
     * 查询大厅公共抢单列表
     */
    Page<CompanionOrder> getHallOrders(int pageNum, int pageSize);

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
     * 开黑服务中一键加钟/续费 (支持多局/多小时续单)
     */
    PaymentResultVO addOnOrder(Long clientUserId, String orderSn, int addQuantity);

    /**
     * 校验并自动触发接单超时退款 (10分钟未接单全额原路退款)
     */
    boolean checkAndRefundIfTimeout(String orderSn);
}
