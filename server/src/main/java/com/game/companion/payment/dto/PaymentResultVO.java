package com.game.companion.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付调起结果响应")
public class PaymentResultVO {

    @Schema(description = "业务订单号")
    private String orderSn;

    @Schema(description = "支付方式: mock / wechat")
    private String payType;

    @Schema(description = "是否已自动完成支付 (在 mock 模式下为 true)")
    private Boolean isMockPaid;

    @Schema(description = "支付流水凭证/预支付单号")
    private String prepayId;

    // 以下为微信原生 JSAPI 调起支付所需五要素参数 (wx.requestPayment)
    @Schema(description = "时间戳")
    private String timeStamp;

    @Schema(description = "随机字符串")
    private String nonceStr;

    @Schema(description = "统一下单数据包 package")
    private String packageVal;

    @Schema(description = "签名类型: RSA / MD5")
    private String signType;

    @Schema(description = "签名")
    private String paySign;
}
