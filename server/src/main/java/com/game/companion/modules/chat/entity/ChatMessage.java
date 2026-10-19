package com.game.companion.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
@Schema(description = "开黑即时聊天记录实体")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联业务订单号")
    private String orderSn;

    @Schema(description = "发送人用户ID")
    private Long senderId;

    @Schema(description = "发送人昵称")
    private String senderName;

    @Schema(description = "发送人头像")
    private String senderAvatar;

    @Schema(description = "消息正文")
    private String content;

    @Schema(description = "发送时间")
    private LocalDateTime createTime;
}
