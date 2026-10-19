package com.game.companion.modules.chat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.game.companion.common.api.CommonResult;
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

    @PostMapping("/send")
    @Operation(summary = "发送开黑聊天消息")
    public CommonResult<ChatMessage> sendMessage(@Valid @RequestBody SendChatDTO dto) {
        Long senderId = dto.getSenderId();
        if (senderId == null || senderId <= 0) {
            senderId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : 10000L;
        }
        ChatMessage message = chatMessageService.sendMessage(senderId, dto);
        return CommonResult.success(message);
    }

    @GetMapping("/messages")
    @Operation(summary = "获取订单房间的真实聊天历史记录")
    public CommonResult<List<ChatMessage>> getMessages(@RequestParam String orderSn) {
        List<ChatMessage> list = chatMessageService.getOrderMessages(orderSn);
        return CommonResult.success(list);
    }
}
