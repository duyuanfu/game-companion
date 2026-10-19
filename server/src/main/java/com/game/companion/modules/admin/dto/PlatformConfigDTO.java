package com.game.companion.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "平台全局动态运营与微信支付配置入参/出参")
public class PlatformConfigDTO {

    @Schema(description = "支付模式: mock(开发测试) / wechat(微信真实支付)")
    private String paymentMode;

    @Schema(description = "平台抽佣比例 (百分比数字，如 10 代表抽佣 10%)")
    private Double commissionPercent;

    @Schema(description = "小程序 AppID")
    private String appId;

    @Schema(description = "小程序 AppSecret")
    private String appSecret;

    @Schema(description = "微信支付商户号 (MchId)")
    private String mchId;

    @Schema(description = "微信支付 APIv3 密钥 (32位)")
    private String apiV3Key;

    @Schema(description = "商户API私钥证书内容 (apiclient_key.pem文本，可直接粘贴)")
    private String apiClientKeyContent;

    @Schema(description = "新订单微信订阅消息模板 ID")
    private String newOrderTemplateId;
}
