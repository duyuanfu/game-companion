package com.game.companion.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 【陪练师技能项目视图】(含游戏图标, 用于下单页直观展示)
 */
@Data
@Builder
@Schema(description = "陪练师游戏技能项目 (含游戏图标)")
public class CompanionSkillVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "技能ID")
    private Long id;

    @Schema(description = "陪练师档案ID")
    private Long companionId;

    @Schema(description = "陪玩师用户ID")
    private Long userId;

    @Schema(description = "游戏品类ID")
    private Long gameId;

    @Schema(description = "游戏名称")
    private String gameName;

    @Schema(description = "游戏图标 URL (来自 game_category.icon)")
    private String gameIcon;

    @Schema(description = "当前段位")
    private String rankName;

    @Schema(description = "段位认证截图")
    private String rankImg;

    @Schema(description = "单价 (分)")
    private Integer price;

    @Schema(description = "单价 (元, 便于前端直接展示)")
    private Double priceYuan;

    @Schema(description = "计价单位: 小时 / 局")
    private String unit;

    @Schema(description = "上架状态：0-下架 1-上架")
    private Integer status;
}
