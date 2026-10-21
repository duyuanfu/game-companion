package com.game.companion.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.mapper.ChatMessageMapper;
import com.game.companion.modules.chat.service.ChatMessageService;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    /**
     * 咨询房间号前缀：CONSULT_{minUserId}_{maxUserId}
     */
    private static final String CONSULT_ROOM_PREFIX = "CONSULT_";

    private final SysUserMapper sysUserMapper;
    private final CompanionOrderMapper companionOrderMapper;

    @Override
    public ChatMessage sendMessage(Long senderId, SendChatDTO dto) {
        SysUser sender = sysUserMapper.selectById(senderId);
        if (sender == null) {
            throw new ApiException("发送人用户不存在");
        }

        // 房间访问权限校验：只能在自己参与的房间里发言，杜绝越权注入他人会话
        if (!canAccessRoom(senderId, sender.getRole() == null ? RoleEnum.CLIENT.getCode() : sender.getRole(), dto.getOrderSn())) {
            throw new ApiException("操作被拒绝：您不是该聊天室的成员，无法发送消息");
        }

        ChatMessage msg = new ChatMessage();
        msg.setOrderSn(dto.getOrderSn());
        msg.setSenderId(sender.getId());
        msg.setSenderName(sender.getNickname());
        msg.setSenderAvatar(sender.getAvatarUrl());
        msg.setContent(dto.getContent().trim());
        msg.setCreateTime(LocalDateTime.now());

        save(msg);
        return msg;
    }

    @Override
    public List<ChatMessage> getOrderMessages(String orderSn) {
        return list(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getOrderSn, orderSn)
                        .orderByAsc(ChatMessage::getCreateTime)
        );
    }

    @Override
    public boolean canAccessRoom(Long userId, int userRole, String roomKey) {
        if (userId == null || roomKey == null || roomKey.trim().isEmpty()) {
            return false;
        }
        if (userRole == RoleEnum.ADMIN.getCode()) {
            return true;
        }

        // 1. 咨询房间 (先聊后买)：CONSULT_{min}_{max}
        if (roomKey.startsWith(CONSULT_ROOM_PREFIX)) {
            String[] parts = roomKey.substring(CONSULT_ROOM_PREFIX.length()).split("_");
            if (parts.length == 2) {
                try {
                    long a = Long.parseLong(parts[0]);
                    long b = Long.parseLong(parts[1]);
                    return userId == a || userId == b;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return false;
        }

        // 2. 订单房间：必须是该订单的下单客户或接单陪玩师
        CompanionOrder order = companionOrderMapper.selectOne(
                new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, roomKey)
        );
        if (order == null) {
            return false;
        }
        return userId.equals(order.getClientUserId())
                || userId.equals(order.getCompanionUserId())
                || userId.equals(order.getOriginalCompanionId());
    }
}
