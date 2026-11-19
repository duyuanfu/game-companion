package com.game.companion.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.entity.ChatReadState;
import com.game.companion.modules.chat.entity.ChatRoom;
import com.game.companion.modules.chat.mapper.ChatReadStateMapper;
import com.game.companion.modules.chat.mapper.ChatRoomMapper;
import com.game.companion.modules.chat.service.ChatRoomService;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private static final String CONSULT_PREFIX = "CONSULT_";

    private final ChatRoomMapper chatRoomMapper;
    private final ChatReadStateMapper chatReadStateMapper;
    private final CompanionOrderMapper companionOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touch(String roomSn, Long senderId, ChatMessage lastMessage) {
        if (roomSn == null || roomSn.isBlank()) {
            return;
        }
        ChatRoom room = chatRoomMapper.selectOne(
                new LambdaQueryWrapper<ChatRoom>().eq(ChatRoom::getRoomSn, roomSn)
        );
        if (room == null && lastMessage == null) {
            return;
        }

        boolean consult = roomSn.startsWith(CONSULT_PREFIX);
        int roomType = consult ? 2 : 1;
        String orderSn = consult ? null : roomSn;
        Long userA = null;
        Long userB = null;

        if (consult) {
            Long[] members = parseConsultMembers(roomSn);
            if (members != null) {
                userA = members[0];
                userB = members[1];
            }
        } else {
            CompanionOrder order = companionOrderMapper.selectOne(
                    new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, roomSn)
            );
            if (order != null) {
                userA = order.getClientUserId();
                userB = order.getCompanionUserId();
            } else if (room != null) {
                userA = room.getUserAId();
                userB = room.getUserBId();
            } else {
                userA = senderId;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (room == null) {
            room = new ChatRoom();
            room.setRoomSn(roomSn);
            room.setRoomType(roomType);
            room.setOrderSn(orderSn);
            room.setUserAId(userA);
            room.setUserBId(userB);
            room.setCreateTime(now);
            room.setUpdateTime(now);
            applyLastMessage(room, lastMessage);
            try {
                chatRoomMapper.insert(room);
                return;
            } catch (DuplicateKeyException e) {
                // 两个用户同时在新房间发第一条消息时，唯一键可能竞争；回查后更新
                room = chatRoomMapper.selectOne(
                        new LambdaQueryWrapper<ChatRoom>().eq(ChatRoom::getRoomSn, roomSn)
                );
                if (room == null) {
                    return;
                }
            }
        }

        room.setRoomType(roomType);
        room.setOrderSn(orderSn);
        room.setUserAId(userA);
        room.setUserBId(userB);
        room.setUpdateTime(now);
        applyLastMessage(room, lastMessage);
        chatRoomMapper.updateById(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void migrateConsultToOrder(String consultRoomSn, String orderSn, Long clientUserId, Long companionUserId) {
        if (consultRoomSn == null || orderSn == null) {
            return;
        }
        // 咨询已读状态同步迁移到订单房间，避免下单后突然全部变成未读
        chatReadStateMapper.delete(
                new LambdaQueryWrapper<ChatReadState>().eq(ChatReadState::getRoomSn, orderSn)
        );
        chatReadStateMapper.update(null,
                new LambdaUpdateWrapper<ChatReadState>()
                        .eq(ChatReadState::getRoomSn, consultRoomSn)
                        .set(ChatReadState::getRoomSn, orderSn)
        );

        ChatRoom consultRoom = chatRoomMapper.selectOne(
                new LambdaQueryWrapper<ChatRoom>().eq(ChatRoom::getRoomSn, consultRoomSn)
        );
        if (consultRoom == null) {
            return;
        }

        ChatRoom existingOrderRoom = chatRoomMapper.selectOne(
                new LambdaQueryWrapper<ChatRoom>().eq(ChatRoom::getRoomSn, orderSn)
        );
        if (existingOrderRoom != null) {
            // 订单房间已存在（例如已有支付系统消息），保留订单房间并删除咨询摘要
            existingOrderRoom.setUserAId(clientUserId);
            existingOrderRoom.setUserBId(companionUserId);
            existingOrderRoom.setRoomType(1);
            existingOrderRoom.setOrderSn(orderSn);
            existingOrderRoom.setUpdateTime(LocalDateTime.now());
            if (consultRoom.getLastMessageId() != null
                    && (existingOrderRoom.getLastMessageId() == null
                    || consultRoom.getLastMessageId() > existingOrderRoom.getLastMessageId())) {
                existingOrderRoom.setLastMessageId(consultRoom.getLastMessageId());
                existingOrderRoom.setLastMessageTime(consultRoom.getLastMessageTime());
            }
            chatRoomMapper.updateById(existingOrderRoom);
            chatRoomMapper.deleteById(consultRoom.getId());
            return;
        }

        consultRoom.setRoomSn(orderSn);
        consultRoom.setRoomType(1);
        consultRoom.setOrderSn(orderSn);
        consultRoom.setUserAId(clientUserId);
        consultRoom.setUserBId(companionUserId);
        consultRoom.setUpdateTime(LocalDateTime.now());
        try {
            chatRoomMapper.updateById(consultRoom);
        } catch (DuplicateKeyException e) {
            chatRoomMapper.deleteById(consultRoom.getId());
            log.warn("咨询房间迁移时订单房间已存在，已删除重复咨询摘要: {}", consultRoomSn);
        }
    }

    @Override
    public java.util.List<Long> memberIds(String roomSn) {
        if (roomSn == null || roomSn.isBlank()) {
            return java.util.List.of();
        }
        ChatRoom room = chatRoomMapper.selectOne(
                new LambdaQueryWrapper<ChatRoom>().eq(ChatRoom::getRoomSn, roomSn)
        );
        if (room != null) {
            return java.util.stream.Stream.of(room.getUserAId(), room.getUserBId())
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (roomSn.startsWith(CONSULT_PREFIX)) {
            Long[] members = parseConsultMembers(roomSn);
            return members == null ? java.util.List.of() : java.util.List.of(members[0], members[1]);
        }
        CompanionOrder order = companionOrderMapper.selectOne(
                new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, roomSn)
        );
        if (order == null) {
            return java.util.List.of();
        }
        return java.util.stream.Stream.of(order.getClientUserId(), order.getCompanionUserId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private void applyLastMessage(ChatRoom room, ChatMessage message) {
        if (message == null || message.getId() == null) {
            return;
        }
        if (room.getLastMessageId() == null || message.getId() > room.getLastMessageId()) {
            room.setLastMessageId(message.getId());
            room.setLastMessageTime(message.getCreateTime());
        }
    }

    private Long[] parseConsultMembers(String roomSn) {
        String[] parts = roomSn.substring(CONSULT_PREFIX.length()).split("_");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new Long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
