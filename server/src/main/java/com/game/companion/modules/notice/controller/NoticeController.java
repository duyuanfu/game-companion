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
@Tag(name = "消息通知接口", description = "平台线下通知事件队列 (替代已下线的微信订阅消息机制)")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/test-send")
    @Operation(summary = "【诊断】生成一条测试用待通知事件")
    public CommonResult<String> testCreateNotice(@RequestParam(defaultValue = "20001") Long companionUserId) {
        String testOrderSn = "TEST_" + System.currentTimeMillis();
        noticeService.createNewOrderNotice(companionUserId, testOrderSn, "王者荣耀 (测试)", 2500, "测试平台线下通知事件");
        return CommonResult.success("待通知事件已生成，请在管理员手机端工作台的「待线下通知」中查看！");
    }
}
