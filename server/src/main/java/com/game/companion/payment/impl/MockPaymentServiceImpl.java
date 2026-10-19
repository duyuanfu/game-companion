package com.game.companion.payment.impl;

import cn.hutool.core.util.IdUtil;
import com.game.companion.payment.PaymentService;
import com.game.companion.payment.dto.CreatePaymentParam;
import com.game.companion.payment.dto.PaymentResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("mockPaymentService")
public class MockPaymentServiceImpl implements PaymentService {

    @Override
    public String getChannelType() {
        return "mock";
    }

    @Override
    public PaymentResultVO createPayment(CreatePaymentParam param) {
        log.info("【Mock 模拟支付】收到下单请求: orderSn={}, amount={}分", param.getOrderSn(), param.getAmount());
        
        // 模拟支付无需调用微信网关，直接生成虚拟预支付号并标记为模拟支付
        String mockPrepayId = "mock_prepay_" + IdUtil.fastSimpleUUID();
        
        return PaymentResultVO.builder()
                .orderSn(param.getOrderSn())
                .payType("mock")
                .isMockPaid(true)
                .prepayId(mockPrepayId)
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .nonceStr(IdUtil.fastSimpleUUID())
                .packageVal("prepay_id=" + mockPrepayId)
                .signType("MOCK")
                .paySign("mock_sign_pass")
                .build();
    }

    @Override
    public boolean refund(String orderSn, Integer totalAmount, Integer refundAmount, String reason) {
        log.info("【Mock 模拟退款】退款成功: orderSn={}, 退款金额={}分, 原因={}", orderSn, refundAmount, reason);
        return true;
    }

    @Override
    public String transferToUser(String withdrawSn, String userOpenId, String realName, Integer amount, String remark) {
        String mockBatchId = "mock_batch_" + IdUtil.fastSimpleUUID();
        log.info("【Mock 模拟打款提现】打款成功: withdrawSn={}, 收款人={}, 金额={}分, 批次号={}", 
                withdrawSn, realName, amount, mockBatchId);
        return mockBatchId;
    }
}
