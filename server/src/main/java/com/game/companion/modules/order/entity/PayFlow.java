package com.game.companion.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("pay_flow")
@Schema(description = "支付流水事实实体")
public class PayFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "内部支付流水单号 (PAY_xxx)")
    private String paySn;

    @Schema(description = "关联业务订单号")
    private String orderSn;

    @Schema(description = "付款客户用户ID")
    private Long clientUserId;

    @Schema(description = "支付方式: mock / wechat_pay / alipay")
    private String payType;

    @Schema(description = "第三方支付机构单号 (如微信支付交易流水号)")
    private String transactionId;

    @Schema(description = "实际支付金额 (单位: 分)")
    private Integer payAmount;

    @Schema(description = "第三方通道手续费扣款 (微信0.6%，单位: 分)")
    private Integer channelFee;

    @Schema(description = "支付状态: 0-待支付 1-支付成功 2-支付失败 3-已全额退款 4-部分退款")
    private Integer payStatus;

    @Schema(description = "支付完成时间")
    private LocalDateTime payTime;

    @Schema(description = "累计已退款金额 (分)")
    private Integer refundAmount;

    @Schema(description = "退款冲正的通道费 (分)")
    private Integer refundChannelFee;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
