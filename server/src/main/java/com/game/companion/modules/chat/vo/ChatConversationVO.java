package com.game.companion.modules.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "消息会话摘要")
public class ChatConversationVO {

    @Schema(description = "聊天室标识：订单号或 CONSULT_{min}_{max}")
    private String roomSn;

    @Schema(description = "房间类型：order-订单房间，consult-咨询房间")
    private String roomType;

    @Schema(description = "对方用户ID")
    private Long peerUserId;

    @Schema(description = "对方昵称")
    private String peerName;

    @Schema(description = "对方头像")
    private String peerAvatar;

    @Schema(description = "最后一条消息内容")
    private String lastMessage;

    @Schema(description = "最后一条消息时间")
    private LocalDateTime lastMessageTime;

    @Schema(description = "最后一条消息发送人ID")
    private Long lastSenderId;

    @Schema(description = "最后一条消息类型：0-用户消息 1-系统消息")
    private Integer lastMessageType;

    @Schema(description = "当前用户在该房间的最后已读时间")
    private LocalDateTime lastReadTime;

    @Schema(description = "关联订单的游戏名称（仅订单房间）")
    private String gameName;

    @Schema(description = "关联订单状态（仅订单房间）")
    private Integer orderStatus;
}
