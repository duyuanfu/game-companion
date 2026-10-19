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
}
