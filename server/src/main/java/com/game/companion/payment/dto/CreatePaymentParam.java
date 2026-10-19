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

    @Schema(description = "客户微信 OpenId")
    private String clientOpenid;
}
