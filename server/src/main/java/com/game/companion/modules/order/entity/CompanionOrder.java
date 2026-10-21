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

    @Schema(description = "订单标价总额 (分 = 单价 x 数量)")
    private Integer originalAmount;

    @Schema(description = "优惠立减金额 (分)")
    private Integer discountAmount;

    @Schema(description = "买家应付/实付总额 (分)")
    private Integer totalAmount;

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

    @Schema(description = "订单履约状态: 0-待付款 1-待接单 2-服务沟通中 3-申请结单 4-已完成 5-已取消 6-已退款 7-维权争议中 8-仲裁退款完成")
    private Integer orderStatus;

    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;

    @Schema(description = "完结时间")
    private LocalDateTime completeTime;

    @Schema(description = "结单战绩截图凭证")
    private String finishScreenshot;

    @Schema(description = "买家发起维权争议原因")
    private String disputeReason;

    @Schema(description = "发起争议维权时间")
    private LocalDateTime disputeTime;

    @Schema(description = "平台管理员仲裁处理说明")
    private String disputeResult;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ================= 领域解耦：非数据库物理字段 (通过关联查询聚合) =================
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "关联的真实支付流水 (来自pay_flow表)")
    private PayFlow payFlow;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "关联的佣金分润清算事实 (来自order_settlement表)")
    private OrderSettlement settlement;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "预计/实际陪练师到手收益 (分，动态聚合供前端无缝展示)")
    private Integer companionIncome;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "预计/实际平台毛抽佣 (分，动态聚合供前端无缝展示)")
    private Integer platformFee;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "当前阶段剩余倒计时秒数（待接单=距超时退款；服务中=距履约超时退款）。由服务端按绝对截止时间计算，前端直接展示不会重置")
    private Integer remainingSeconds;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "当前阶段截止时间")
    private java.time.LocalDateTime deadlineTime;
}
