package com.game.companion.modules.notice.controller;

import com.game.companion.common.api.CommonResult;
import com.game.companion.modules.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
@Tag(name = "消息通知诊断接口", description = "用于独立测试向陪练师微信直接发送订阅消息卡片")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/test-send")
    @Operation(summary = "【诊断神器】直接向指定陪练师微信推送测试卡片消息")
    public CommonResult<String> testSendSubscribeMsg(@RequestParam(defaultValue = "20001") Long companionUserId) {
        String testOrderSn = "TEST_" + System.currentTimeMillis();
        noticeService.sendNewOrderNotice(companionUserId, testOrderSn, "王者荣耀 (测试)", 2500, "测试真实微信卡片直推");
        return CommonResult.success("推送指令已发出，请查看 IDEA 控制台微信官方返回的详细日志，并检查手机微信「服务通知」！");
    }
}
