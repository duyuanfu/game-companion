package com.game.companion.modules.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.common.api.CommonResult;
import com.game.companion.common.security.AuthGuard;
import com.game.companion.modules.admin.dto.AuditApplyDTO;
import com.game.companion.modules.admin.dto.PlatformConfigDTO;
import com.game.companion.modules.admin.service.AdminDashboardService;
import com.game.companion.modules.admin.service.PlatformConfigService;
import com.game.companion.modules.admin.vo.AdminDashboardVO;
import com.game.companion.modules.admin.vo.CompanionAuditItemVO;
import com.game.companion.modules.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "手机端小程序管理员工作台", description = "供超级管理员在手机小程序内一键听语音、审资质、看大盘")
public class AdminCompanionController {

    private final AdminDashboardService adminDashboardService;
    private final PlatformConfigService platformConfigService;
    private final NoticeService noticeService;
    private final AuthGuard authGuard;

    @GetMapping("/config/get")
    @Operation(summary = "【管理员手机端】获取当前平台运营与微信支付热配置")
    public CommonResult<PlatformConfigDTO> getPlatformConfig() {
        authGuard.requireAdmin();
        return CommonResult.success(platformConfigService.getPlatformConfig());
    }

    @PostMapping("/config/save")
    @Operation(summary = "【管理员手机端】保存并热更新平台运营与微信支付参数")
    public CommonResult<String> savePlatformConfig(@RequestBody PlatformConfigDTO dto) {
        // 商户密钥与平台抽佣参数属于最高权限配置，必须管理员本人操作
        authGuard.requireAdmin();
        platformConfigService.savePlatformConfig(dto);
        return CommonResult.success("配置已成功更新并秒级热生效！");
    }

    @GetMapping("/audit/list")
    @Operation(summary = "【管理员手机端】获取待审核陪玩师入驻申请列表")
    public CommonResult<Page<CompanionAuditItemVO>> getPendingAudits(@RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "10") int pageSize) {
        authGuard.requireAdmin();
        Page<CompanionAuditItemVO> result = adminDashboardService.getPendingAudits(pageNum, pageSize);
        return CommonResult.success(result);
    }

    @PostMapping("/audit/decision")
    @Operation(summary = "【管理员手机端】一键审核决定 (通过 / 快捷驳回)")
    public CommonResult<String> auditDecision(@Valid @RequestBody AuditApplyDTO dto) {
        authGuard.requireAdmin();
        adminDashboardService.processAudit(dto);
        return CommonResult.success(dto.getIsApproved() ? "已通过入驻审核，微信已通知该陪玩师" : "已驳回申请，微信已通知原因");
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "【管理员手机端】经营大盘总览 (今日订单、总流水、平台抽佣)")
    public CommonResult<AdminDashboardVO> getDashboard() {
        authGuard.requireAdmin();
        AdminDashboardVO dashboard = adminDashboardService.getDashboardSummary();
        return CommonResult.success(dashboard);
    }

    @GetMapping("/order/pending-accept")
    @Operation(summary = "【管理员手机端】待接单订单列表 (含陪玩师联系方式，供线下通知)")
    public CommonResult<Page<com.game.companion.modules.admin.vo.AdminOrderItemVO>> getPendingAcceptOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        authGuard.requireAdmin();
        return CommonResult.success(adminDashboardService.getPendingAcceptOrders(pageNum, pageSize));
    }

    @GetMapping("/notice/pending")
    @Operation(summary = "【管理员手机端】待线下通知事件队列 (新订单/审核结果/维权)")
    public CommonResult<java.util.List<com.game.companion.modules.notice.vo.NoticeItemVO>> getPendingNotices(
            @RequestParam(defaultValue = "50") int limit) {
        authGuard.requireAdmin();
        return CommonResult.success(noticeService.getPendingNotices(limit));
    }

    @PostMapping("/notice/ack")
    @Operation(summary = "【管理员手机端】标记某条事件已完成线下通知")
    public CommonResult<String> ackNotice(@RequestParam Long noticeId,
                                          @RequestParam(required = false) String remark) {
        authGuard.requireAdmin();
        noticeService.markNotified(noticeId, remark);
        return CommonResult.success("已标记为通知完成！");
    }
}
