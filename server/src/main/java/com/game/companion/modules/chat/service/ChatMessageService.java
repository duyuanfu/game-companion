package com.game.companion.modules.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.vo.ChatConversationVO;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    ChatMessage sendMessage(Long senderId, SendChatDTO dto);

    List<ChatMessage> getOrderMessages(String orderSn);

    /**
     * 汇总当前用户参与的订单房间与咨询房间，按最后消息时间倒序返回。
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<ChatConversationVO> listConversations(
            Long userId, int pageNum, int pageSize);

    /**
     * 记录当前用户在指定聊天室的最后已读时间。
     */
    void markRoomRead(Long userId, String roomSn);

    /**
     * 校验当前用户是否有权访问指定聊天室
     * (订单房间：下单客户 / 接单陪玩师 / 平台管理员；咨询房间：发起方 / 接收方)
     */
    boolean canAccessRoom(Long userId, int userRole, String roomKey);
}
