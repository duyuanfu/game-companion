package com.game.companion.modules.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("game_category")
@Schema(description = "游戏品类实体")
public class GameCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "游戏名称")
    private String name;

    @Schema(description = "游戏图标")
    private String icon;

    @Schema(description = "排序权重")
    private Integer sort;

    @Schema(description = "是否启用: 0-禁用 1-启用")
    private Integer isActive;

    private LocalDateTime createTime;
}
