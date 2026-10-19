package com.game.companion.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "入驻审核提交参数")
public class AuditApplyDTO {

    @NotNull(message = "申请记录ID不能为空")
    @Schema(description = "陪玩师档案 profileId")
    private Long profileId;

    @NotNull(message = "审核结果不能为空")
    @Schema(description = "是否通过: true-通过 false-驳回")
    private Boolean isApproved;

    @Schema(description = "驳回原因 (如: 音频杂音大/段位截图不清晰)")
    private String rejectReason;
}
