package com.game.companion.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("companion_order")
@Schema(description = "陪玩预约订单实体")
public class CompanionOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "订单业务流水号")
    private String orderSn;

    @Schema(description = "下单客户ID")
    private Long clientUserId;

    @Schema(description = "接单陪玩师ID")
    private Long companionUserId;

    @Schema(description = "购买的技能ID")
    private Long skillId;

    @Schema(description = "游戏名称")
    private String gameName;

    @Schema(description = "单价 (分)")
    private Integer unitPrice;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "订单总额 (分)")
    private Integer totalAmount;

    @Schema(description = "平台抽佣 (分)")
    private Integer platformFee;

    @Schema(description = "陪玩师收入 (分)")
    private Integer companionIncome;

    @Schema(description = "订单类型：1-指定陪玩单 2-大厅公共抢单")
    private Integer orderType;

    @Schema(description = "是否已超时流转至大厅：0-否 1-是")
    private Integer isTransferred;

    @Schema(description = "原指定陪玩师ID")
    private Long originalCompanionId;

    @Schema(description = "游戏区服")
    private String gameServer;

    @Schema(description = "客户游戏昵称/账号")
    private String gameAccount;

    @Schema(description = "客户需求备注")
    private String clientRemark;

    @Schema(description = "订单状态: 0-待付款 1-待接单 2-服务沟通中 3-申请结单 4-已完成 5-已取消 6-已退款")
    private Integer orderStatus;

    @Schema(description = "支付状态: 0-未支付 1-已支付 2-已退款")
    private Integer payStatus;

    @Schema(description = "支付方式: mock / wechat")
    private String payType;

    @Schema(description = "第三方支付流水号")
    private String transactionId;

    @Schema(description = "付款完成时间")
    private LocalDateTime payTime;

    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;

    @Schema(description = "完结时间")
    private LocalDateTime completeTime;

    @Schema(description = "结算到账时间")
    private LocalDateTime settleTime;

    @Schema(description = "结单战绩截图凭证")
    private String finishScreenshot;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
