package com.game.companion.modules.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "管理员审核提现打款入参")
public class AuditWithdrawDTO {

    @NotNull(message = "提现记录ID不能为空")
    @Schema(description = "提现记录ID")
    private Long withdrawId;

    @NotNull(message = "审核结果不能为空")
    @Schema(description = "是否批准打款: true-同意打款 false-驳回")
    private Boolean isApproved;

    @Schema(description = "驳回理由")
    private String rejectReason;

    @Schema(description = "打款通道: manual(人工打款) / wechat_transfer(微信商家转账)，默认 manual")
    private String payoutChannel;

    @Schema(description = "人工打款备注/银行流水号/支付宝流水号")
    private String payoutRemark;

    @Schema(description = "人工打款凭证 URL")
    private String payoutVoucher;
}
