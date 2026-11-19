package com.game.companion.payment.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.game.companion.common.exception.ApiException;
import com.game.companion.payment.PaymentService;
import com.game.companion.payment.dto.CreatePaymentParam;
import com.game.companion.payment.dto.PaymentResultVO;
import com.github.binarywang.wxpay.bean.merchanttransfer.TransferCreateRequest;
import com.github.binarywang.wxpay.bean.merchanttransfer.TransferCreateResult;
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayRefundV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

/**
 * 【微信支付 V3 生产级实现】
 *
 * <p>覆盖三大能力：</p>
 * <ol>
 *   <li><b>JSAPI 下单</b>：统一下单 + RSA 签名，前端直接唤起 {@code wx.requestPayment}；</li>
 *   <li><b>原路退款</b>：调用 V3 退款接口，退款金额与原单金额一并上送，金额不一致微信会直接拒单；</li>
 *   <li><b>商家转账到零钱</b>：陪玩师提现打款，返回微信批次单号用于对账。</li>
 * </ol>
 *
 * <p><b>重要</b>：本类绝不「静默假装成功」。商户参数缺失、签名失败、微信返回非成功状态时
 * 一律抛出 {@link ApiException}，避免平台在真实资金链路上出现假成功导致的资损。</p>
 */
@Slf4j
@Service("wechatPaymentService")
@RequiredArgsConstructor
public class WechatV3PaymentServiceImpl implements PaymentService {

    private final com.game.companion.common.config.WxPayServiceProvider wxPayServiceProvider;

    /**
     * 支付结果回调地址（微信异步通知），需公网可达。
     */
    @Value("${wx.pay.notify-url:}")
    private String notifyUrl;

    /**
     * 商家转账到零钱的场景 ID（需在微信商户平台申请「付款到零钱」经营场景）。
     */
    @Value("${wx.pay.transfer-scene-id:}")
    private String transferSceneId;

    /**
     * H5 支付 scene_info.h5_info.app_url 使用的站点地址。
     */
    @Value("${wx.pay.web-app-url:}")
    private String webAppUrl;

    /**
     * 当前生效的商户服务实例（随管理员热配置自动重建）。
     */
    private WxPayService wxPayService() {
        return wxPayServiceProvider.getWxPayService();
    }

    @Override
    public String getChannelType() {
        return "wechat";
    }

    @Override
    public PaymentResultVO createPayment(CreatePaymentParam param) {
        WxPayConfig config = requirePayConfig();
        String scene = param.getPayScene() == null ? "native" : param.getPayScene().trim().toLowerCase();
        log.info("【微信支付V3】发起真实统一下单: orderSn={}, amount={}分, scene={}",
                param.getOrderSn(), param.getAmount(), scene);

        WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request()
                .setAppid(config.getAppId())
                .setMchid(config.getMchId())
                .setDescription(param.getDescription())
                .setOutTradeNo(param.getOrderSn())
                .setNotifyUrl(resolveNotifyUrl(config))
                .setAmount(new WxPayUnifiedOrderV3Request.Amount()
                        .setTotal(param.getAmount())
                        .setCurrency("CNY"));

        if ("h5".equals(scene)) {
            if (isBlank(param.getClientIp())) {
                throw new ApiException("H5 支付缺少客户端 IP，无法下单");
            }
            String appUrl = resolveWebAppUrl(param.getReturnUrl());
            request.setSceneInfo(new WxPayUnifiedOrderV3Request.SceneInfo()
                    .setPayerClientIp(param.getClientIp().trim())
                    .setH5Info(new WxPayUnifiedOrderV3Request.H5Info()
                            .setType("Wap")
                            .setAppName("PP电竞鼠")
                            .setAppUrl(appUrl)));

            WxPayUnifiedOrderV3Result result;
            try {
                result = wxPayService().createOrderV3(TradeTypeEnum.H5, request);
            } catch (Exception e) {
                log.error("【微信支付V3】H5 下单失败: orderSn={}, error={}", param.getOrderSn(), e.getMessage());
                throw new ApiException("微信 H5 支付下单失败：" + e.getMessage());
            }
            if (result == null || isBlank(result.getH5Url())) {
                throw new ApiException("微信 H5 支付下单失败：未获取到 h5_url");
            }
            return PaymentResultVO.builder()
                    .orderSn(param.getOrderSn())
                    .payType("wechat")
                    .isMockPaid(false)
                    .tradeType("H5")
                    .h5Url(appendRedirectUrl(result.getH5Url(), param.getReturnUrl()))
                    .build();
        }

        if ("jsapi".equals(scene)) {
            if (isBlank(param.getClientOpenid())) {
                throw new ApiException("JSAPI 支付缺少客户 openid");
            }
            request.setPayer(new WxPayUnifiedOrderV3Request.Payer()
                    .setOpenid(param.getClientOpenid()));
            WxPayUnifiedOrderV3Result result;
            try {
                result = wxPayService().createOrderV3(TradeTypeEnum.JSAPI, request);
            } catch (Exception e) {
                log.error("【微信支付V3】JSAPI 下单失败: orderSn={}, error={}", param.getOrderSn(), e.getMessage());
                throw new ApiException("微信支付下单失败：" + e.getMessage());
            }
            if (result == null || isBlank(result.getPrepayId())) {
                throw new ApiException("微信支付下单失败：未获取到 prepay_id");
            }
            WxPayUnifiedOrderV3Result.JsapiResult jsapi = result.getPayInfo(
                    TradeTypeEnum.JSAPI, config.getAppId(), config.getMchId(), config.getPrivateKey());
            return PaymentResultVO.builder()
                    .orderSn(param.getOrderSn())
                    .payType("wechat")
                    .isMockPaid(false)
                    .tradeType("JSAPI")
                    .prepayId(result.getPrepayId())
                    .timeStamp(jsapi.getTimeStamp())
                    .nonceStr(jsapi.getNonceStr())
                    .packageVal(jsapi.getPackageValue())
                    .signType(jsapi.getSignType())
                    .paySign(jsapi.getPaySign())
                    .build();
        }

        // 默认 Native：PC 浏览器扫码支付
        WxPayUnifiedOrderV3Result nativeResult;
        try {
            nativeResult = wxPayService().createOrderV3(TradeTypeEnum.NATIVE, request);
        } catch (Exception e) {
            log.error("【微信支付V3】Native 下单失败: orderSn={}, error={}", param.getOrderSn(), e.getMessage());
            throw new ApiException("微信 Native 支付下单失败：" + e.getMessage());
        }
        if (nativeResult == null || isBlank(nativeResult.getCodeUrl())) {
            throw new ApiException("微信 Native 支付下单失败：未获取到 code_url");
        }
        return PaymentResultVO.builder()
                .orderSn(param.getOrderSn())
                .payType("wechat")
                .isMockPaid(false)
                .tradeType("NATIVE")
                .codeUrl(nativeResult.getCodeUrl())
                .build();
    }

    @Override
    public boolean refund(String orderSn, Integer totalAmount, Integer refundAmount, String reason) {
        WxPayConfig config = requirePayConfig();
        log.info("【微信支付V3】发起原路退款: orderSn={}, 退款金额={}分, 原因={}", orderSn, refundAmount, reason);

        String outRefundNo = "REFUND_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
        WxPayRefundV3Request request = new WxPayRefundV3Request()
                .setOutTradeNo(orderSn)
                .setOutRefundNo(outRefundNo)
                .setReason(reason == null || reason.isEmpty() ? "用户申请退款" : reason)
                .setNotifyUrl(resolveNotifyUrl(config))
                .setAmount(new WxPayRefundV3Request.Amount()
                        .setRefund(refundAmount)
                        .setTotal(totalAmount)
                        .setCurrency("CNY"));

        WxPayRefundV3Result result;
        try {
            result = wxPayService().refundV3(request);
        } catch (Exception e) {
            log.error("【微信支付V3】退款调用失败: orderSn={}, error={}", orderSn, e.getMessage());
            throw new ApiException("微信支付退款失败：" + e.getMessage());
        }

        if (result == null) {
            throw new ApiException("微信支付退款失败：未获得微信应答");
        }
        // 微信退款状态：SUCCESS-成功 / PROCESSING-处理中 / 失败
        if (!"SUCCESS".equalsIgnoreCase(result.getStatus()) && !"PROCESSING".equalsIgnoreCase(result.getStatus())) {
            throw new ApiException("微信支付退款失败，微信返回状态：" + result.getStatus());
        }

        log.info("【微信支付V3】退款受理成功: orderSn={}, outRefundNo={}, status={}, refundId={}",
                orderSn, result.getOutRefundNo(), result.getStatus(), result.getRefundId());
        return true;
    }

    @Override
    public String transferToUser(String withdrawSn, String userOpenId, String realName, Integer amount, String remark) {
        WxPayConfig config = requirePayConfig();
        if (transferSceneId == null || transferSceneId.trim().isEmpty()) {
            throw new ApiException("商家转账到零钱未配置场景ID (wx.pay.transfer-scene-id)，无法发起打款！");
        }
        log.info("【微信支付V3】商家转账到零钱: outBatchNo={}, 收款人={}, 金额={}分",
                withdrawSn, realName, amount);

        TransferCreateRequest.TransferDetailList detail = new TransferCreateRequest.TransferDetailList()
                .setOutDetailNo(withdrawSn)
                .setTransferAmount(amount)
                .setTransferRemark(remark == null ? "收益提现" : remark)
                .setOpenid(userOpenId);
        // 收款人姓名：2023 年起微信要求「用户姓名」与 openid 对应的实名一致时才可传，否则会报错
        if (realName != null && !realName.trim().isEmpty()) {
            detail.setUserName(realName.trim());
        }

        TransferCreateRequest request = new TransferCreateRequest()
                .setAppid(config.getAppId())
                .setOutBatchNo(withdrawSn)
                .setBatchName("开黑陪练平台收益提现")
                .setBatchRemark(remark == null ? "收益提现" : remark)
                .setTotalAmount(amount)
                .setTotalNum(1)
                .setTransferDetailList(Collections.singletonList(detail))
                .setTransferSceneId(transferSceneId.trim());

        TransferCreateResult result;
        try {
            result = wxPayService().getMerchantTransferService().createTransfer(request);
        } catch (Exception e) {
            log.error("【微信支付V3】商家转账失败: outBatchNo={}, error={}", withdrawSn, e.getMessage());
            throw new ApiException("微信商家转账失败：" + e.getMessage());
        }

        if (result == null || result.getBatchId() == null || result.getBatchId().isEmpty()) {
            throw new ApiException("微信商家转账失败：未获得微信批次单号");
        }
        log.info("【微信支付V3】商家转账受理成功: outBatchNo={}, batchId={}", withdrawSn, result.getBatchId());
        return result.getBatchId();
    }

    /**
     * 校验微信支付商户配置完整性，缺失时立即失败，绝不降级为假成功。
     */
    private WxPayConfig requirePayConfig() {
        WxPayService service = wxPayService();
        if (service == null) {
            throw new ApiException("微信支付商户号未配置，请先在管理员热配置中心填写完整商户信息或切换回 mock 模式");
        }
        WxPayConfig config = service.getConfig();
        if (config == null
                || config.getMchId() == null || config.getMchId().isEmpty()
                || config.getApiV3Key() == null || config.getApiV3Key().isEmpty()
                || config.getAppId() == null || config.getAppId().isEmpty()) {
            throw new ApiException("微信支付商户参数不完整 (appId / mchId / apiV3Key)，请在管理员热配置中心补全");
        }
        return config;
    }

    private String resolveNotifyUrl(WxPayConfig config) {
        if (notifyUrl != null && !notifyUrl.trim().isEmpty()) {
            return notifyUrl.trim();
        }
        return config.getNotifyUrl();
    }

    private String resolveWebAppUrl(String returnUrl) {
        if (webAppUrl != null && !webAppUrl.trim().isEmpty()) {
            return webAppUrl.trim();
        }
        if (returnUrl != null && !returnUrl.trim().isEmpty()) {
            return returnUrl.trim();
        }
        throw new ApiException("H5 支付需要配置 wx.pay.web-app-url 或 WEB_BASE_URL");
    }

    private String appendRedirectUrl(String h5Url, String returnUrl) {
        if (returnUrl == null || returnUrl.trim().isEmpty()) {
            return h5Url;
        }
        String separator = h5Url.contains("?") ? "&" : "?";
        return h5Url + separator + "redirect_url="
                + URLEncoder.encode(returnUrl.trim(), StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
