package com.game.companion.modules.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    ChatMessage sendMessage(Long senderId, SendChatDTO dto);

    List<ChatMessage> getOrderMessages(String orderSn);
}
