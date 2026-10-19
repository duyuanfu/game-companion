package com.game.companion.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.mapper.ChatMessageMapper;
import com.game.companion.modules.chat.service.ChatMessageService;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    private final SysUserMapper sysUserMapper;

    @Override
    public ChatMessage sendMessage(Long senderId, SendChatDTO dto) {
        SysUser sender = sysUserMapper.selectById(senderId);
        if (sender == null) {
            throw new ApiException("发送人用户不存在");
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
}
