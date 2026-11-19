package com.game.companion.modules.chat.controller;

import com.game.companion.common.api.CommonResult;
import com.game.companion.common.api.ResultCode;
import com.game.companion.common.exception.ApiException;
import com.game.companion.common.security.AuthGuard;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.service.ChatEventService;
import com.game.companion.modules.chat.service.ChatMessageService;
import com.game.companion.modules.chat.vo.ChatConversationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "即时聊天接口", description = "客户与陪玩师在线沟通、会话汇总")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatEventService chatEventService;
    private final AuthGuard authGuard;

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 实时事件流（聊天消息 / 订单事件）")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
            @RequestParam String token,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        if (token == null || token.isBlank()) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        Object loginId;
        try {
            loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdByToken(token);
        } catch (Exception e) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        if (loginId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return chatEventService.subscribe(Long.valueOf(loginId.toString()));
    }

    @GetMapping("/conversations")
    @Operation(summary = "获取当前用户的消息会话列表")
    public CommonResult<com.baomidou.mybatisplus.extension.plugins.pagination.Page<ChatConversationVO>> listConversations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "30") int pageSize) {
        return CommonResult.success(chatMessageService.listConversations(
                authGuard.currentUserId(), pageNum, pageSize));
    }

    @PostMapping("/read")
    @Operation(summary = "标记聊天室已读到最新")
    public CommonResult<String> markRead(@RequestParam String roomSn) {
        Long userId = authGuard.currentUserId();
        int role = authGuard.currentRole();
        if (!chatMessageService.canAccessRoom(userId, role, roomSn)) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        chatMessageService.markRoomRead(userId, roomSn);
        return CommonResult.success("已读");
    }

    @PostMapping("/send")
    @Operation(summary = "发送聊天消息")
    public CommonResult<ChatMessage> sendMessage(@Valid @RequestBody SendChatDTO dto) {
        // 发送人只能是当前登录用户本人，忽略 DTO 中传入的 senderId
        Long senderId = authGuard.currentUserId();
        ChatMessage message = chatMessageService.sendMessage(senderId, dto);
        return CommonResult.success(message);
    }

    @GetMapping("/messages")
    @Operation(summary = "获取订单房间的真实聊天历史记录 (仅限房间成员)")
    public CommonResult<List<ChatMessage>> getMessages(@RequestParam String orderSn) {
        Long currentUserId = authGuard.currentUserId();
        int currentRole = authGuard.currentRole();
        if (!chatMessageService.canAccessRoom(currentUserId, currentRole, orderSn)) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        List<ChatMessage> list = chatMessageService.getOrderMessages(orderSn);
        return CommonResult.success(list);
    }
}
