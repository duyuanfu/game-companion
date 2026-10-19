package com.game.companion.modules.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "手机端待审核陪玩师卡片VO")
public class CompanionAuditItemVO {

    @Schema(description = "档案ID")
    private Long profileId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "申请人昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatarUrl;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "声音录音URL (手机端点击直接播放试听)")
    private String voiceUrl;

    @Schema(description = "录音时长(秒)")
    private Integer voiceDuration;

    @Schema(description = "擅长游戏及段位截图")
    private String rankImg;

    @Schema(description = "段位描述 (如: 最强王者30星)")
    private String rankName;

    @Schema(description = "个人简介")
    private String bio;

    @Schema(description = "申请提交时间")
    private LocalDateTime applyTime;
}
