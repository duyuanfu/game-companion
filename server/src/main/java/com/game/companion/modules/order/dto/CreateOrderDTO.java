package com.game.companion.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "客户预约下单请求参数")
public class CreateOrderDTO {

    @Schema(description = "指定陪玩师ID (大厅单可不填)")
    private Long companionUserId;

    @NotNull(message = "技能服务ID不能为空")
    @Schema(description = "技能服务ID")
    private Long skillId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    @Schema(description = "购买数量(局/小时)")
    private Integer quantity;

    @NotBlank(message = "游戏区服不能为空")
    @Schema(description = "游戏区服 (如: 王者荣耀 微信大区)")
    private String gameServer;

    @NotBlank(message = "游戏账号不能为空")
    @Schema(description = "游戏昵称/账号")
    private String gameAccount;

    @Schema(description = "需求备注/段位要求")
    private String clientRemark;
}
