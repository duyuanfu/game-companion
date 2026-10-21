package com.game.companion.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("order_settlement")
@Schema(description = "订单分佣清算实体")
public class OrderSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "清算流水号 (SETTLE_xxx)")
    private String settleSn;

    @Schema(description = "关联业务订单号")
    private String orderSn;

    @Schema(description = "下单客户ID")
    private Long clientUserId;

    @Schema(description = "履约陪练师ID")
    private Long companionUserId;

    @Schema(description = "订单总交易额 (分)")
    private Integer totalAmount;

    @Schema(description = "陪练师最终结算收益 (分)")
    private Integer companionIncome;

    @Schema(description = "平台账面毛抽佣 (分)")
    private Integer platformGrossFee;

    @Schema(description = "扣除的第三方微信通道费 (分，按0.6%计算)")
    private Integer channelFee;

    @Schema(description = "平台真实到手净利润 (分 = gross - channel)")
    private Integer platformNetProfit;

    @Schema(description = "通道费承担主体: 1-平台承担 2-陪练师承担 3-买家承担")
    private Integer feePayer;

    @Schema(description = "清算状态: 1-已清算入账 2-已冲正驳回")
    private Integer settleStatus;

    @Schema(description = "清算完成时间")
    private LocalDateTime settleTime;

    private LocalDateTime createTime;
}
