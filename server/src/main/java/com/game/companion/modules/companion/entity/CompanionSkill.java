package com.game.companion.modules.companion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("companion_skill")
@Schema(description = "陪玩师技能与定价表")
public class CompanionSkill implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companionId;

    private Long userId;

    private Long gameId;

    private String gameName;

    private String rankName;

    private String rankImg;

    private Integer price; // 分

    private String unit;

    private Integer status;

    private LocalDateTime createTime;
}
