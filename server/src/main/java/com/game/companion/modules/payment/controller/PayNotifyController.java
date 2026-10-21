package com.game.companion.modules.payment.controller;

import com.game.companion.common.config.WxPayServiceProvider;
import com.game.companion.modules.order.service.CompanionOrderService;
import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【微信支付 V3 异步回调网关】
 *
 * <p>真实支付模式下，微信支付成功后会向本接口推送加密的支付结果通知。
 * 本接口负责：验签解密 → 校验交易状态 → 落盘支付事实 (pay_flow) → 订单流转「待接单」→ 触发派单通知。</p>
 *
 * <p><b>安全说明</b>：本接口由微信服务器回调，不走登录态鉴权（登录态拦截器已将其列入白名单），
 * 但请求体必须通过微信官方 RSA 验签与 AES-GCM 解密，任何伪造请求都会被拒绝。</p>
 */
@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayNotifyController {

    private final WxPayServiceProvider wxPayServiceProvider;
    private final CompanionOrderService companionOrderService;

    @PostMapping(value = "/notify", produces = MediaType.APPLICATION_JSON_VALUE)
    public String notifyPayResult(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {

        WxPayService wxPayService = wxPayServiceProvider.getWxPayService();
        if (wxPayService == null) {
            log.error("【微信支付回调】商户服务未配置，无法处理回调");
            return fail("MERCHANT_NOT_CONFIGURED");
        }
        if (timestamp == null || nonce == null || signature == null || serial == null) {
            log.warn("【微信支付回调】缺少验签请求头，拒绝处理");
            return fail("MISSING_SIGNATURE_HEADERS");
        }

        WxPayNotifyV3Result notifyResult;
        try {
            SignatureHeader signatureHeader = new SignatureHeader(timestamp, nonce, signature, serial);
            notifyResult = wxPayService.parseOrderNotifyV3Result(body, signatureHeader);
        } catch (Exception e) {
            log.error("【微信支付回调】验签/解密失败: {}", e.getMessage());
            return fail("SIGNATURE_VERIFY_FAILED");
        }

        if (notifyResult == null || notifyResult.getResult() == null) {
            log.error("【微信支付回调】回调内容解析为空");
            return fail("EMPTY_NOTIFY_RESULT");
        }

        WxPayNotifyV3Result.DecryptNotifyResult result = notifyResult.getResult();
        if (!"SUCCESS".equalsIgnoreCase(result.getTradeState())) {
            log.warn("【微信支付回调】交易状态非成功: orderSn={}, tradeState={}",
                    result.getOutTradeNo(), result.getTradeState());
            return success();
        }

        Integer paidAmount = (result.getAmount() != null && result.getAmount().getTotal() != null)
                ? result.getAmount().getTotal() : null;

        try {
            companionOrderService.recordPaySuccess(
                    result.getOutTradeNo(), "wechat_pay", result.getTransactionId(), paidAmount);
            log.info("【微信支付回调处理成功】orderSn={}, transactionId={}, amount={}分",
                    result.getOutTradeNo(), result.getTransactionId(), paidAmount);
        } catch (Exception e) {
            log.error("【微信支付回调】落盘支付事实失败: orderSn={}, error={}", result.getOutTradeNo(), e.getMessage());
            return fail("PROCESS_FAILED");
        }

        return success();
    }

    /**
     * 微信要求：处理成功返回 HTTP 200 且响应体为 {"code":"SUCCESS"}
     */
    private String success() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    private String fail(String code) {
        return "{\"code\":\"" + code + "\",\"message\":\"失败\"}";
    }
}
