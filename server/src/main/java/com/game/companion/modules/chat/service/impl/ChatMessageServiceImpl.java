package com.game.companion.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.entity.ChatReadState;
import com.game.companion.modules.chat.entity.ChatRoom;
import com.game.companion.modules.chat.mapper.ChatMessageMapper;
import com.game.companion.modules.chat.mapper.ChatReadStateMapper;
import com.game.companion.modules.chat.mapper.ChatRoomMapper;
import com.game.companion.modules.chat.service.ChatEventService;
import com.game.companion.modules.chat.service.ChatMessageService;
import com.game.companion.modules.chat.service.ChatRoomService;
import com.game.companion.modules.chat.vo.ChatConversationVO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ChatReadStateMapper chatReadStateMapper;
    private final ChatRoomMapper chatRoomMapper;
    private final ChatRoomService chatRoomService;
    private final ChatEventService chatEventService;

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        msg.setMessageType(0);
        msg.setSenderName(sender.getNickname());
        msg.setSenderAvatar(sender.getAvatarUrl());
        msg.setContent(dto.getContent().trim());
        msg.setCreateTime(LocalDateTime.now());

        save(msg);
        chatRoomService.touch(dto.getOrderSn(), senderId, msg);
        chatEventService.publish(
                chatRoomService.memberIds(dto.getOrderSn()),
                "chat",
                Map.of("type", "chat", "roomSn", dto.getOrderSn(), "message", msg)
        );
        return msg;
    }

    @Override
    public void markRoomRead(Long userId, String roomSn) {
        if (userId == null || roomSn == null || roomSn.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ChatReadState state = chatReadStateMapper.selectOne(
                new LambdaQueryWrapper<ChatReadState>()
                        .eq(ChatReadState::getUserId, userId)
                        .eq(ChatReadState::getRoomSn, roomSn)
        );
        if (state == null) {
            state = new ChatReadState();
            state.setUserId(userId);
            state.setRoomSn(roomSn);
            state.setLastReadTime(now);
            state.setUpdateTime(now);
            try {
                chatReadStateMapper.insert(state);
                return;
            } catch (DuplicateKeyException e) {
                // 多端/多请求同时标记已读时，唯一键会竞争；回查后走更新分支
                state = chatReadStateMapper.selectOne(
                        new LambdaQueryWrapper<ChatReadState>()
                                .eq(ChatReadState::getUserId, userId)
                                .eq(ChatReadState::getRoomSn, roomSn)
                );
                if (state == null) {
                    return;
                }
            }
        }
        if (state.getLastReadTime() == null || now.isAfter(state.getLastReadTime())) {
            state.setLastReadTime(now);
            state.setUpdateTime(now);
            chatReadStateMapper.updateById(state);
        }
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
    public Page<ChatConversationVO> listConversations(Long userId, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        if (userId == null) {
            return emptyConversationPage(safePage, safeSize);
        }

        Page<ChatRoom> roomPage = new Page<>(safePage, safeSize);
        chatRoomMapper.selectPage(roomPage,
                new LambdaQueryWrapper<ChatRoom>()
                        .and(w -> w.eq(ChatRoom::getUserAId, userId).or().eq(ChatRoom::getUserBId, userId))
                        .orderByDesc(ChatRoom::getLastMessageTime)
        );
        List<ChatRoom> rooms = roomPage.getRecords();
        if (rooms == null || rooms.isEmpty()) {
            return emptyConversationPage(safePage, safeSize);
        }

        List<Long> messageIds = rooms.stream()
                .map(ChatRoom::getLastMessageId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ChatMessage> messageMap = messageIds.isEmpty()
                ? Map.of()
                : baseMapper.selectBatchIds(messageIds).stream()
                        .collect(Collectors.toMap(ChatMessage::getId, message -> message, (a, b) -> a));

        Set<Long> peerIds = new HashSet<>();
        for (ChatRoom room : rooms) {
            Long peerId = userId.equals(room.getUserAId()) ? room.getUserBId() : room.getUserAId();
            if (peerId != null) {
                peerIds.add(peerId);
            }
        }
        Map<Long, SysUser> userMap = peerIds.isEmpty()
                ? Map.of()
                : sysUserMapper.selectBatchIds(peerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, user -> user, (a, b) -> a));

        Set<String> orderSns = rooms.stream()
                .map(ChatRoom::getOrderSn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, CompanionOrder> orderMap = orderSns.isEmpty()
                ? Map.of()
                : companionOrderMapper.selectList(
                        new LambdaQueryWrapper<CompanionOrder>().in(CompanionOrder::getOrderSn, orderSns)
                ).stream().collect(Collectors.toMap(CompanionOrder::getOrderSn, order -> order, (a, b) -> a));

        Set<String> roomSns = rooms.stream().map(ChatRoom::getRoomSn).collect(Collectors.toSet());
        Map<String, LocalDateTime> readTimes = roomSns.isEmpty()
                ? Map.of()
                : chatReadStateMapper.selectList(
                        new LambdaQueryWrapper<ChatReadState>()
                                .eq(ChatReadState::getUserId, userId)
                                .in(ChatReadState::getRoomSn, roomSns)
                ).stream().collect(Collectors.toMap(ChatReadState::getRoomSn, ChatReadState::getLastReadTime, (a, b) -> a));

        List<ChatConversationVO> result = new ArrayList<>();
        for (ChatRoom room : rooms) {
            ChatMessage latest = messageMap.get(room.getLastMessageId());
            if (latest == null) {
                continue;
            }
            ChatConversationVO vo = new ChatConversationVO();
            vo.setRoomSn(room.getRoomSn());
            vo.setRoomType(room.getRoomType() != null && room.getRoomType() == 2 ? "consult" : "order");
            vo.setLastMessage(latest.getContent());
            vo.setLastMessageTime(latest.getCreateTime());
            vo.setLastSenderId(latest.getSenderId());
            vo.setLastMessageType(latest.getMessageType());
            vo.setLastReadTime(readTimes.get(room.getRoomSn()));

            Long peerId = userId.equals(room.getUserAId()) ? room.getUserBId() : room.getUserAId();
            vo.setPeerUserId(peerId);
            if (room.getOrderSn() != null) {
                CompanionOrder order = orderMap.get(room.getOrderSn());
                if (order != null) {
                    vo.setGameName(order.getGameName());
                    vo.setOrderStatus(order.getOrderStatus());
                    if (order.getClientUserId() != null && order.getCompanionUserId() != null) {
                        vo.setPeerUserId(userId.equals(order.getClientUserId())
                                ? order.getCompanionUserId() : order.getClientUserId());
                    }
                }
            }
            peerId = vo.getPeerUserId();
            SysUser peer = peerId == null ? null : userMap.get(peerId);
            if (peer != null) {
                vo.setPeerName(peer.getNickname());
                vo.setPeerAvatar(peer.getAvatarUrl());
            } else if (peerId != null) {
                vo.setPeerName("已注销用户");
            }
            result.add(vo);
        }

        Page<ChatConversationVO> page = new Page<>(safePage, safeSize);
        page.setTotal(roomPage.getTotal());
        page.setRecords(result);
        return page;
    }

    private Page<ChatConversationVO> emptyConversationPage(int pageNum, int pageSize) {
        Page<ChatConversationVO> page = new Page<>(pageNum, pageSize);
        page.setTotal(0);
        page.setRecords(List.of());
        return page;
    }

    /**
     * 解析咨询房间的对方用户ID；不是当前用户的咨询房间时返回 null。
     */
    private Long consultPeerId(String roomSn, Long userId) {
        if (roomSn == null || !roomSn.startsWith(CONSULT_ROOM_PREFIX)) {
            return null;
        }
        String[] parts = roomSn.substring(CONSULT_ROOM_PREFIX.length()).split("_");
        if (parts.length != 2) {
            return null;
        }
        try {
            long a = Long.parseLong(parts[0]);
            long b = Long.parseLong(parts[1]);
            if (userId == a) {
                return b;
            }
            if (userId == b) {
                return a;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
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
