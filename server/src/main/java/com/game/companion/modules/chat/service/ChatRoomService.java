package com.game.companion.modules.chat.service;

import com.game.companion.modules.chat.entity.ChatMessage;

public interface ChatRoomService {

    /**
     * 更新聊天室摘要（最后一条消息、成员、房间类型），不存在时自动创建。
     */
    void touch(String roomSn, Long senderId, ChatMessage lastMessage);

    /**
     * 咨询转订单时，把咨询房间摘要迁移为订单房间摘要。
     */
    void migrateConsultToOrder(String consultRoomSn, String orderSn, Long clientUserId, Long companionUserId);

    /**
     * 返回聊天室成员ID，用于消息/订单事件推送。
     */
    java.util.List<Long> memberIds(String roomSn);
}
