package com.game.companion.modules.companion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "申请成为陪练师入参 (支持多游戏技能申报)")
public class ApplyCompanionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "实名姓名 (平台实名备案)")
    private String realName;

    @NotBlank(message = "联系手机不能为空")
    @Schema(description = "手机号码 (用于接单提醒)")
    private String phone;

    @Schema(description = "个性简介/自我介绍")
    private String bio;

    @Schema(description = "原声音频介绍 URL")
    private String voiceUrl;

    @Schema(description = "音频录音时长 (秒)")
    private Integer voiceDuration;

    @NotEmpty(message = "请至少添加一个擅长游戏服务项目")
    @Schema(description = "申报的游戏服务项目列表 (支持多游戏)")
    private List<SkillItemDTO> skills;

    @Data
    @Schema(description = "单个游戏服务项目申报")
    public static class SkillItemDTO {
        @NotBlank(message = "游戏名称不能为空")
        @Schema(description = "游戏品类名称 (如王者荣耀)")
        private String gameName;

        @NotBlank(message = "当前段位不能为空")
        @Schema(description = "游戏当前段位 (如荣耀王者)")
        private String rankName;

        @Schema(description = "期望单价 (默认单位: 元/小时，如 25)")
        private Integer price;

        @Schema(description = "price 字段的单位: YUAN-元 (默认) / FEN-分。为空时按「元」处理")
        private String priceUnit;

        @Schema(description = "计价单位: 小时 / 局")
        private String unit;

        @Schema(description = "游戏段位认证截图")
        private String rankImg;
    }
}
