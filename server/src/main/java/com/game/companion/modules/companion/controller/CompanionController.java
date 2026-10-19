package com.game.companion.modules.companion.controller;

import com.game.companion.common.api.CommonResult;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.service.CompanionService;
import com.game.companion.modules.companion.vo.CompanionItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companion")
@RequiredArgsConstructor
@Tag(name = "陪玩师精选列表", description = "首页展示陪玩师卡片，包含原声试听、真实段位、定价")
public class CompanionController {

    private final CompanionService companionService;

    @GetMapping("/list")
    @Operation(summary = "从数据库查询审核通过的陪练师列表")
    public CommonResult<List<CompanionItemVO>> getCompanions(@RequestParam(required = false) String gameName) {
        List<CompanionItemVO> list = companionService.listCompanions(gameName);
        return CommonResult.success(list);
    }

    @GetMapping("/skills")
    @Operation(summary = "从数据库查询陪练师支持的所有游戏技能项目与对应单价")
    public CommonResult<List<CompanionSkill>> getCompanionSkills(@RequestParam Long companionUserId) {
        List<CompanionSkill> list = companionService.getCompanionSkills(companionUserId);
        return CommonResult.success(list);
    }

    @GetMapping("/workbench/stats")
    @Operation(summary = "【陪玩师工作台】从数据库获取真实统计数据 (余额、接单状态、单数)")
    public CommonResult<com.game.companion.modules.companion.vo.CompanionWorkbenchVO> getWorkbenchStats(@RequestParam(required = false) Long userId) {
        Long targetUserId = userId != null ? userId : cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return CommonResult.success(companionService.getWorkbenchStats(targetUserId));
    }

    @PostMapping("/toggle-online")
    @Operation(summary = "【陪玩师工作台】切换真实接单状态")
    public CommonResult<String> toggleOnline(@RequestParam(required = false) Long userId, @RequestParam boolean isOnline) {
        Long targetUserId = userId != null ? userId : cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        companionService.toggleOnlineStatus(targetUserId, isOnline);
        return CommonResult.success(isOnline ? "已切换为接单中" : "已切换为休息中");
    }
}
