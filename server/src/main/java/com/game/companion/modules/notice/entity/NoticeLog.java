package com.game.companion.modules.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 【待通知事件表】
 *
 * <p>因微信小程序订阅消息为一次性机制、无法保证陪练师及时收到消息，
 * 平台改为：所有需要触达用户的事件先落库为「待通知」记录，
 * 由管理员在手机端工作台查看并执行线下通知（电话 / 微信 / 短信）。</p>
 */
@Data
@TableName("notice_log")
@Schema(description = "待通知事件实体 (平台线下通知队列)")
public class NoticeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联订单号 (无订单关联的事件可为空)")
    private String orderSn;

    @Schema(description = "需要被通知的用户ID (陪玩师 / 入驻申请人)")
    private Long targetUserId;

    @Schema(description = "通知类型：1-新订单待接单 2-陪练师已接单 3-入驻审核结果 4-售后维权")
    private Integer noticeType;

    @Schema(description = "通知渠道：5-平台线下通知 (电话/微信/短信)")
    private Integer channel;

    @Schema(description = "事件标题")
    private String title;

    @Schema(description = "事件内容 (线下通知话术)")
    private String content;

    @Schema(description = "被通知人昵称")
    private String userName;

    @Schema(description = "被通知人手机号 (供管理员线下拨打)")
    private String phone;

    @Schema(description = "关联金额 (分)")
    private Integer amount;

    @Schema(description = "通知状态：0-待通知 1-已通知")
    private Integer sendStatus;

    @Schema(description = "管理员处理备注 (已通知时间与方式)")
    private String responseMsg;

    @Schema(description = "事件发生时间")
    private LocalDateTime createTime;

    @Schema(description = "通知完成时间")
    private LocalDateTime finishTime;
}
