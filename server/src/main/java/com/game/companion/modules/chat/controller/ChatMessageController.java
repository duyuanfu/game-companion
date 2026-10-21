package com.game.companion.modules.chat.controller;

import com.game.companion.common.api.CommonResult;
import com.game.companion.common.api.ResultCode;
import com.game.companion.common.exception.ApiException;
import com.game.companion.common.security.AuthGuard;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "开黑即时聊天接口", description = "用于客户与陪玩师双方真实在线发送消息、同步对话")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final AuthGuard authGuard;

    @PostMapping("/send")
    @Operation(summary = "发送开黑聊天消息")
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
