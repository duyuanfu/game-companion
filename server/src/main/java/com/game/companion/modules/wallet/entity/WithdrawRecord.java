package com.game.companion.modules.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("withdraw_record")
@Schema(description = "陪玩师提现记录实体")
public class WithdrawRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "提现业务流水号")
    private String withdrawSn;

    @Schema(description = "申请陪玩师用户ID")
    private Long userId;

    @Schema(description = "申请提现金额(分)")
    private Integer amount;

    @Schema(description = "手续费(分)")
    private Integer fee;

    @Schema(description = "实际到账金额(分)")
    private Integer actualAmount;

    @Schema(description = "提现通道: mock / wechat_transfer")
    private String channel;

    @Schema(description = "提现状态: 0-待审核 1-打款中 2-提现成功 3-提现失败/已驳回")
    private Integer status;

    @Schema(description = "微信商家转账批次单号")
    private String wechatBatchId;

    @Schema(description = "失败或驳回原因")
    private String failReason;

    @Schema(description = "申请时间")
    private LocalDateTime createTime;

    @Schema(description = "打款完成时间")
    private LocalDateTime finishTime;
}
