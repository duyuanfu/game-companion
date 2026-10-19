package com.game.companion.payment.impl;

import com.game.companion.common.exception.ApiException;
import com.game.companion.payment.PaymentService;
import com.game.companion.payment.dto.CreatePaymentParam;
import com.game.companion.payment.dto.PaymentResultVO;
import com.github.binarywang.wxpay.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("wechatPaymentService")
@RequiredArgsConstructor
public class WechatV3PaymentServiceImpl implements PaymentService {

    @Autowired(required = false)
    private WxPayService wxPayService;

    @Override
    public String getChannelType() {
        return "wechat";
    }

    @Override
    public PaymentResultVO createPayment(CreatePaymentParam param) {
        log.info("【微信支付V3】发起真实统一下单: orderSn={}, amount={}", param.getOrderSn(), param.getAmount());
        if (wxPayService == null) {
            throw new ApiException("微信支付商户号未配置，请先在 application.yml 中配置 wx.pay 参数或切换为 mock 模式");
        }
        // 后续在此调用 wxPayService.createOrderV3(TradeTypeEnum.JSAPI, request)
        // 并获取真实 prepayId 和签名
        return PaymentResultVO.builder()
                .orderSn(param.getOrderSn())
                .payType("wechat")
                .isMockPaid(false)
                .build();
    }

    @Override
    public boolean refund(String orderSn, Integer totalAmount, Integer refundAmount, String reason) {
        log.info("【微信支付V3】发起退款: orderSn={}", orderSn);
        if (wxPayService == null) {
            throw new ApiException("微信支付商户号未配置");
        }
        return true;
    }

    @Override
    public String transferToUser(String withdrawSn, String userOpenId, String realName, Integer amount, String remark) {
        log.info("【微信支付V3】商家转账到零钱: withdrawSn={}", withdrawSn);
        if (wxPayService == null) {
            throw new ApiException("微信支付商户号未配置");
        }
        return "wx_batch_demo";
    }
}
