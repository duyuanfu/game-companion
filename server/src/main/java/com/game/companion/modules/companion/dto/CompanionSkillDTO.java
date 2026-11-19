package com.game.companion.modules.companion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "陪练师技能新增/编辑入参")
public class CompanionSkillDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "游戏名称不能为空")
    @Size(max = 64, message = "游戏名称不能超过 64 个字符")
    @Schema(description = "游戏名称，如 王者荣耀")
    private String gameName;

    @NotBlank(message = "当前段位不能为空")
    @Size(max = 64, message = "段位不能超过 64 个字符")
    @Schema(description = "当前段位，如 荣耀王者")
    private String rankName;

    @NotNull(message = "单价不能为空")
    @Min(value = 1, message = "单价不能低于 0.01 元")
    @Max(value = 10000000, message = "单价不能高于 100000 元")
    @Schema(description = "单价（分）")
    private Integer price;

    @Schema(description = "计价单位：小时 / 局")
    private String unit;

    @Schema(description = "上架状态：0-下架 1-上架")
    private Integer status;

    @Schema(description = "段位认证截图")
    private String rankImg;
}
