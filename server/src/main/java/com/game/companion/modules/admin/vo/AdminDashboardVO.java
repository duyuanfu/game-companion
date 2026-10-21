package com.game.companion.modules.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "手机端管理看板总览数据")
public class AdminDashboardVO {

    @Schema(description = "今日订单总数")
    private Integer todayOrderCount;

    @Schema(description = "今日交易总流水 (分)")
    private Integer todayTotalAmount;

    @Schema(description = "今日平台累计毛抽佣 (分)")
    private Integer todayPlatformFee;

    @Schema(description = "今日微信支付官方通道扣点成本 (0.6%，单位: 分)")
    private Integer todayChannelFee;

    @Schema(description = "今日平台真实净利润 (分 = 平台毛抽佣 - 微信通道费)")
    private Integer todayNetProfit;

    @Schema(description = "当前在线接单中陪玩师人数")
    private Integer onlineCompanionCount;

    @Schema(description = "待审核陪玩师数量")
    private Integer pendingAuditCount;
}
