package com.game.companion.modules.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 【待通知事件视图】(管理员手机端线下通知工作台使用)
 */
@Data
@Builder
@Schema(description = "待线下通知事件")
public class NoticeItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "事件ID")
    private Long id;

    @Schema(description = "关联订单号")
    private String orderSn;

    @Schema(description = "通知类型：1-新订单待接单 2-陪练师已接单 3-入驻审核结果 4-售后维权")
    private Integer noticeType;

    @Schema(description = "类型文案")
    private String typeText;

    @Schema(description = "事件标题")
    private String title;

    @Schema(description = "事件内容 / 线下通知话术")
    private String content;

    @Schema(description = "被通知人用户ID")
    private Long targetUserId;

    @Schema(description = "被通知人昵称")
    private String userName;

    @Schema(description = "被通知人手机号 (供平台线下拨打)")
    private String phone;

    @Schema(description = "关联金额 (分)")
    private Integer amount;

    @Schema(description = "事件发生时间")
    private LocalDateTime createTime;
}
