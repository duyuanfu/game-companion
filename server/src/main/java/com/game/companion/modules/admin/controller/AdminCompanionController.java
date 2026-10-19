package com.game.companion.modules.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.common.api.CommonResult;
import com.game.companion.modules.admin.dto.AuditApplyDTO;
import com.game.companion.modules.admin.dto.PlatformConfigDTO;
import com.game.companion.modules.admin.service.AdminDashboardService;
import com.game.companion.modules.admin.service.PlatformConfigService;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import com.game.companion.modules.admin.vo.AdminDashboardVO;
import com.game.companion.modules.admin.vo.CompanionAuditItemVO;
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

    @GetMapping("/config/get")
    @Operation(summary = "【管理员手机端】获取当前平台运营与微信支付热配置")
    public CommonResult<PlatformConfigDTO> getPlatformConfig() {
        return CommonResult.success(platformConfigService.getPlatformConfig());
    }

    @PostMapping("/config/save")
    @Operation(summary = "【管理员手机端】保存并热更新平台运营与微信支付参数")
    public CommonResult<String> savePlatformConfig(@RequestBody PlatformConfigDTO dto) {
        platformConfigService.savePlatformConfig(dto);
        return CommonResult.success("配置已成功更新并秒级热生效！");
    }

    @GetMapping("/audit/list")
    @Operation(summary = "【管理员手机端】获取待审核陪玩师入驻申请列表")
    public CommonResult<Page<CompanionAuditItemVO>> getPendingAudits(@RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "10") int pageSize) {
        Page<CompanionAuditItemVO> result = adminDashboardService.getPendingAudits(pageNum, pageSize);
        return CommonResult.success(result);
    }

    @PostMapping("/audit/decision")
    @Operation(summary = "【管理员手机端】一键审核决定 (通过 / 快捷驳回)")
    public CommonResult<String> auditDecision(@Valid @RequestBody AuditApplyDTO dto) {
        adminDashboardService.processAudit(dto);
        return CommonResult.success(dto.getIsApproved() ? "已通过入驻审核，微信已通知该陪玩师" : "已驳回申请，微信已通知原因");
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "【管理员手机端】经营大盘总览 (今日订单、总流水、平台抽佣)")
    public CommonResult<AdminDashboardVO> getDashboard() {
        AdminDashboardVO dashboard = adminDashboardService.getDashboardSummary();
        return CommonResult.success(dashboard);
    }
}
