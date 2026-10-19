package com.game.companion.payment;

import com.game.companion.payment.dto.CreatePaymentParam;
import com.game.companion.payment.dto.PaymentResultVO;

public interface PaymentService {

    /**
     * 获取当前支付渠道类型标识 (mock / wechat)
     */
    String getChannelType();

    /**
     * 创建支付订单 / 调起凭证
     */
    PaymentResultVO createPayment(CreatePaymentParam param);

    /**
     * 发起退款
     *
     * @param orderSn       业务订单号
     * @param totalAmount   订单总额(分)
     * @param refundAmount  退款金额(分)
     * @param reason        退款原因
     * @return 是否退款成功
     */
    boolean refund(String orderSn, Integer totalAmount, Integer refundAmount, String reason);

    /**
     * 发起提现打款 (商家转账到零钱)
     *
     * @param withdrawSn   提现流水号
     * @param userOpenId   收款人微信 OpenId
     * @param realName     收款人真实姓名
     * @param amount       提现金额(分)
     * @param remark       转账备注
     * @return 第三方业务批次单号
     */
    String transferToUser(String withdrawSn, String userOpenId, String realName, Integer amount, String remark);
}
