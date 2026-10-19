package com.game.companion.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "首页陪玩师展示卡片")
public class CompanionItemVO {

    @Schema(description = "陪玩师档案ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "擅长游戏")
    private String game;

    @Schema(description = "段位")
    private String rank;

    @Schema(description = "单价 (元)")
    private Integer price;

    @Schema(description = "计价单位")
    private String unit;

    @Schema(description = "是否在线接单中")
    private Boolean isOnline;

    @Schema(description = "声音试听时长(秒)")
    private Integer voiceDuration;

    @Schema(description = "声音试听URL")
    private String voiceUrl;

    @Schema(description = "综合评分")
    private BigDecimal rating;

    @Schema(description = "接单总量")
    private Integer orderCount;

    @Schema(description = "个人简介")
    private String bio;

    @Schema(description = "技能ID (用于下单)")
    private Long skillId;
}
