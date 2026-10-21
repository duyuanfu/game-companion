package com.game.companion.modules.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 【待接单订单视图】(管理员手机端线下通知陪玩师时使用)
 *
 * <p>陪玩师长时间未接单时，管理员需要拿到**订单信息 + 陪玩师联系方式**，
 * 才能通过电话 / 微信 / 短信完成线下通知，避免漏单。</p>
 */
@Data
@Builder
@Schema(description = "待接单订单 (含陪玩师联系方式)")
public class AdminOrderItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderSn;

    @Schema(description = "游戏名称")
    private String gameName;

    @Schema(description = "游戏图标")
    private String gameIcon;

    @Schema(description = "订单金额 (分)")
    private Integer totalAmount;

    @Schema(description = "订单状态")
    private Integer orderStatus;

    @Schema(description = "下单客户ID")
    private Long clientUserId;

    @Schema(description = "下单客户昵称")
    private String clientName;

    @Schema(description = "接单陪玩师ID")
    private Long companionUserId;

    @Schema(description = "接单陪玩师昵称")
    private String companionName;

    @Schema(description = "接单陪玩师手机号 (供平台线下拨打)")
    private String companionPhone;

    @Schema(description = "客户需求备注")
    private String clientRemark;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "已等待分钟数")
    private Long waitMinutes;
}
