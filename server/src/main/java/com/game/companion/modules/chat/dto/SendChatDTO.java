package com.game.companion.modules.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发送即时聊天消息入参")
public class SendChatDTO {

    @NotBlank(message = "订单号不能为空")
    @Schema(description = "关联订单号")
    private String orderSn;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "发送人ID (可选，默认取当前登录人)")
    private Long senderId;
}
