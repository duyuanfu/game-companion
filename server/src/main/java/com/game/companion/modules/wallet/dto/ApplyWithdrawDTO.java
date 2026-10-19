package com.game.companion.modules.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "申请提现入参")
public class ApplyWithdrawDTO {

    @NotNull(message = "提现金额不能为空")
    @Min(value = 100, message = "提现金额至少为 1 元 (100分)")
    @Schema(description = "提现金额 (单位: 分)")
    private Integer amount;

    @Schema(description = "提现人真实姓名 (微信商家转账需实名校验)")
    private String realName;
}
