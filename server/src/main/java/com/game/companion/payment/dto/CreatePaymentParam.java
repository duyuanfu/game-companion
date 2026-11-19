package com.game.companion.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "发起支付统一下单入参")
public class CreatePaymentParam {

    @Schema(description = "订单号")
    private String orderSn;

    @Schema(description = "支付金额 (单位: 分)")
    private Integer amount;

    @Schema(description = "商品/服务简要描述")
    private String description;

    @Schema(description = "客户微信 OpenId (仅 JSAPI 需要)")
    private String clientOpenid;

    @Schema(description = "支付场景: native / h5 / jsapi")
    private String payScene;

    @Schema(description = "客户端 IP (H5 支付必填)")
    private String clientIp;

    @Schema(description = "H5 支付完成后的回跳地址")
    private String returnUrl;
}
