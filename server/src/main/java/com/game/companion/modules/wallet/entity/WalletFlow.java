package com.game.companion.modules.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("wallet_flow")
@Schema(description = "钱包资金变动流水实体")
public class WalletFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "流水单号")
    private String flowSn;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "变动类型: 1-订单收益结算 2-发起提现冻结 3-提现成功扣除 4-提现驳回解冻")
    private Integer type;

    @Schema(description = "变动金额(分)")
    private Integer amount;

    @Schema(description = "变动前余额(分)")
    private Integer beforeBalance;

    @Schema(description = "变动后余额(分)")
    private Integer afterBalance;

    @Schema(description = "关联订单号")
    private String orderSn;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}
