package com.game.companion.payment;

import com.game.companion.common.config.WxPayServiceProvider;
import com.game.companion.payment.dto.CreatePaymentParam;
import com.game.companion.payment.dto.PaymentResultVO;
import com.game.companion.payment.impl.WechatV3PaymentServiceImpl;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatV3PaymentServiceImplTest {

    @Mock
    private WxPayServiceProvider provider;

    @Mock
    private WxPayService wxPayService;

    private WechatV3PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        WxPayConfig config = new WxPayConfig();
        config.setAppId("wx-test-appid");
        config.setMchId("1230000109");
        config.setApiV3Key("01234567890123456789012345678901");
        when(provider.getWxPayService()).thenReturn(wxPayService);
        when(wxPayService.getConfig()).thenReturn(config);

        service = new WechatV3PaymentServiceImpl(provider);
        ReflectionTestUtils.setField(service, "notifyUrl", "https://api.example.com/api/pay/notify");
        ReflectionTestUtils.setField(service, "webAppUrl", "https://web.example.com");
    }

    @Test
    void createsNativePaymentAndReturnsCodeUrl() throws Exception {
        WxPayUnifiedOrderV3Result result = new WxPayUnifiedOrderV3Result();
        result.setCodeUrl("weixin://wxpay/bizpayurl?pr=native123");
        when(wxPayService.createOrderV3(eq(TradeTypeEnum.NATIVE), any())).thenReturn(result);

        PaymentResultVO payment = service.createPayment(CreatePaymentParam.builder()
                .orderSn("ORDER_NATIVE_1")
                .amount(100)
                .description("测试商品")
                .payScene("native")
                .build());

        assertEquals("wechat", payment.getPayType());
        assertEquals("NATIVE", payment.getTradeType());
        assertEquals("weixin://wxpay/bizpayurl?pr=native123", payment.getCodeUrl());
        assertNull(payment.getH5Url());

        ArgumentCaptor<WxPayUnifiedOrderV3Request> captor =
                ArgumentCaptor.forClass(WxPayUnifiedOrderV3Request.class);
        org.mockito.Mockito.verify(wxPayService).createOrderV3(eq(TradeTypeEnum.NATIVE), captor.capture());
        assertNull(captor.getValue().getPayer());
        assertEquals("ORDER_NATIVE_1", captor.getValue().getOutTradeNo());
    }

    @Test
    void createsH5PaymentWithSceneInfoAndReturnUrl() throws Exception {
        WxPayUnifiedOrderV3Result result = new WxPayUnifiedOrderV3Result();
        result.setH5Url("https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=h5");
        when(wxPayService.createOrderV3(eq(TradeTypeEnum.H5), any())).thenReturn(result);

        PaymentResultVO payment = service.createPayment(CreatePaymentParam.builder()
                .orderSn("ORDER_H5_1")
                .amount(200)
                .description("测试商品")
                .payScene("h5")
                .clientIp("203.0.113.8")
                .returnUrl("https://web.example.com/orders/ORDER_H5_1")
                .build());

        assertEquals("H5", payment.getTradeType());
        assertEquals("wechat", payment.getPayType());
        assertNotNull(payment.getH5Url());
        assertTrue(payment.getH5Url().contains("redirect_url="));
        assertTrue(payment.getH5Url().contains("https%3A%2F%2Fweb.example.com%2Forders%2FORDER_H5_1"));

        ArgumentCaptor<WxPayUnifiedOrderV3Request> captor =
                ArgumentCaptor.forClass(WxPayUnifiedOrderV3Request.class);
        org.mockito.Mockito.verify(wxPayService).createOrderV3(eq(TradeTypeEnum.H5), captor.capture());
        WxPayUnifiedOrderV3Request request = captor.getValue();
        assertNotNull(request.getSceneInfo());
        assertEquals("203.0.113.8", request.getSceneInfo().getPayerClientIp());
        assertEquals("Wap", request.getSceneInfo().getH5Info().getType());
        assertEquals("https://web.example.com", request.getSceneInfo().getH5Info().getAppUrl());
    }
}
