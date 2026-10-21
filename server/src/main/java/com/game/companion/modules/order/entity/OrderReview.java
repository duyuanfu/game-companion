package com.game.companion.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("order_review")
@Schema(description = "订单评价实体")
public class OrderReview implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联companion_order主键ID")
    private Long orderId;

    @Schema(description = "关联业务订单号")
    private String orderSn;

    @Schema(description = "评价人(客户用户ID)")
    private Long clientUserId;

    @Schema(description = "被评价人(陪玩师用户ID)")
    private Long companionUserId;

    @Schema(description = "综合评分 (1-5星)")
    private Integer score;

    @Schema(description = "游戏技术评分 (1-5星)")
    private Integer skillScore;

    @Schema(description = "服务态度评分 (1-5星)")
    private Integer attitudeScore;

    @Schema(description = "快捷标签 (逗号分隔)")
    private String tags;

    @Schema(description = "评价具体文字")
    private String content;

    @Schema(description = "评价提交时间")
    private LocalDateTime createTime;
}
