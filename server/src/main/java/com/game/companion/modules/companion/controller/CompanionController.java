package com.game.companion.modules.companion.controller;

import com.game.companion.common.api.CommonResult;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.service.CompanionService;
import com.game.companion.modules.companion.vo.CompanionItemVO;
import com.game.companion.common.security.AuthGuard;
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
    private final AuthGuard authGuard;

    @GetMapping("/list")
    @Operation(summary = "从数据库查询审核通过的陪练师列表 (自动屏蔽本人卡片)")
    public CommonResult<List<CompanionItemVO>> getCompanions(@RequestParam(required = false) String gameName) {
        // 公开浏览接口；若已登录则从后端源头屏蔽自己的卡片（防刷单）
        Long currentUserId = authGuard.currentUserIdOrNull();
        List<CompanionItemVO> list = companionService.listCompanions(gameName, currentUserId);
        return CommonResult.success(list);
    }

    @GetMapping("/skills")
    @Operation(summary = "从数据库查询陪练师支持的所有游戏技能项目与对应单价 (含游戏图标)")
    public CommonResult<java.util.List<com.game.companion.modules.companion.vo.CompanionSkillVO>> getCompanionSkills(@RequestParam Long companionUserId) {
        List<com.game.companion.modules.companion.vo.CompanionSkillVO> list = companionService.getCompanionSkillViews(companionUserId);
        return CommonResult.success(list);
    }

    @GetMapping("/workbench/stats")
    @Operation(summary = "【陪玩师工作台】从数据库获取真实统计数据 (余额、接单状态、单数)")
    public CommonResult<com.game.companion.modules.companion.vo.CompanionWorkbenchVO> getWorkbenchStats() {
        // 只能查看自己的工作台数据
        Long targetUserId = authGuard.currentUserId();
        return CommonResult.success(companionService.getWorkbenchStats(targetUserId));
    }

    @GetMapping("/my-status")
    @Operation(summary = "【准入】查询当前登录用户的陪玩师入驻与接单资格状态")
    public CommonResult<com.game.companion.modules.companion.vo.CompanionStatusVO> getMyStatus() {
        // 身份只认当前登录会话
        Long currentUserId = authGuard.currentUserId();
        return CommonResult.success(companionService.getMyCompanionStatus(currentUserId));
    }

    @PostMapping("/toggle-online")
    @Operation(summary = "【陪玩师工作台】切换真实接单状态")
    public CommonResult<String> toggleOnline(@RequestParam boolean isOnline) {
        Long targetUserId = authGuard.currentUserId();
        authGuard.requireCompanion();
        companionService.toggleOnlineStatus(targetUserId, isOnline);
        return CommonResult.success(isOnline ? "已切换为接单中" : "已切换为休息中");
    }

    @GetMapping("/my-skills")
    @Operation(summary = "【陪练师】获取自己的全部技能（含已下架）")
    public CommonResult<List<com.game.companion.modules.companion.vo.CompanionSkillVO>> getMySkills() {
        Long userId = authGuard.currentUserId();
        return CommonResult.success(companionService.getMySkillViews(userId));
    }

    @PostMapping("/skills")
    @Operation(summary = "【陪练师】新增游戏技能与定价")
    public CommonResult<com.game.companion.modules.companion.vo.CompanionSkillVO> createSkill(
            @jakarta.validation.Valid @RequestBody com.game.companion.modules.companion.dto.CompanionSkillDTO dto) {
        Long userId = authGuard.currentUserId();
        return CommonResult.success(companionService.saveMySkill(userId, null, dto), "技能已添加");
    }

    @PutMapping("/skills/{id}")
    @Operation(summary = "【陪练师】编辑自己的游戏技能、价格或上下架状态")
    public CommonResult<com.game.companion.modules.companion.vo.CompanionSkillVO> updateSkill(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.game.companion.modules.companion.dto.CompanionSkillDTO dto) {
        Long userId = authGuard.currentUserId();
        return CommonResult.success(companionService.saveMySkill(userId, id, dto), "技能已更新");
    }

    @PostMapping("/apply")
    @Operation(summary = "【申请入驻】客户申请成为陪练师 (支持申报多个游戏与差异化定价)")
    public CommonResult<String> applyCompanion(@jakarta.validation.Valid @RequestBody com.game.companion.modules.companion.dto.ApplyCompanionDTO dto) {
        Long targetUserId = authGuard.currentUserId();
        companionService.applyCompanion(targetUserId, dto);
        return CommonResult.success("入驻申请提交成功，请等待管理员核验资质！");
    }
}
