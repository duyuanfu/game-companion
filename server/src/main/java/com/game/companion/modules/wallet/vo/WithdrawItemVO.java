package com.game.companion.modules.wallet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "提现申请审核卡片展示VO")
public class WithdrawItemVO {

    @Schema(description = "提现记录ID")
    private Long id;

    @Schema(description = "提现业务单号")
    private String withdrawSn;

    @Schema(description = "陪练师用户ID")
    private Long userId;

    @Schema(description = "申请人昵称")
    private String nickname;

    @Schema(description = "申请人头像")
    private String avatarUrl;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "申请提现金额(分)")
    private Integer amount;

    @Schema(description = "手续费(分)")
    private Integer fee;

    @Schema(description = "实际打款金额(分)")
    private Integer actualAmount;

    @Schema(description = "提现状态: 0-待审核 1-打款中 2-提现成功 3-已驳回")
    private Integer status;

    @Schema(description = "微信转账批次单号")
    private String wechatBatchId;

    @Schema(description = "驳回或失败原因")
    private String failReason;

    @Schema(description = "申请时间")
    private LocalDateTime createTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
}
