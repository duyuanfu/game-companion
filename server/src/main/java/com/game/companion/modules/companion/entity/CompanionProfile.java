package com.game.companion.modules.companion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("companion_profile")
@Schema(description = "陪玩师档案主表")
public class CompanionProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String idCard;

    private String bio;

    private String voiceUrl;

    private Integer voiceDuration;

    private String photos;

    private Integer isOnline;

    private BigDecimal rating;

    private Integer orderCount;

    private Integer auditStatus;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
