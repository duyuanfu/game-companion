package com.game.companion.modules.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    ChatMessage sendMessage(Long senderId, SendChatDTO dto);

    List<ChatMessage> getOrderMessages(String orderSn);

    /**
     * 校验当前用户是否有权访问指定聊天室
     * (订单房间：下单客户 / 接单陪玩师 / 平台管理员；咨询房间：发起方 / 接收方)
     */
    boolean canAccessRoom(Long userId, int userRole, String roomKey);
}
