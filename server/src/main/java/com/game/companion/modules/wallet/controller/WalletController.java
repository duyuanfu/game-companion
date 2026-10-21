package com.game.companion.modules.wallet.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.common.api.CommonResult;
import com.game.companion.common.security.AuthGuard;
import com.game.companion.modules.wallet.dto.ApplyWithdrawDTO;
import com.game.companion.modules.wallet.dto.AuditWithdrawDTO;
import com.game.companion.modules.wallet.entity.WalletFlow;
import com.game.companion.modules.wallet.entity.WithdrawRecord;
import com.game.companion.modules.wallet.service.UserWalletService;
import com.game.companion.modules.wallet.vo.WithdrawItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "钱包与提现结算中心", description = "包含陪练师申请提现、查看收支流水、以及管理员手机端打款审核")
public class WalletController {

    private final UserWalletService userWalletService;
    private final AuthGuard authGuard;

    @PostMapping("/withdraw/apply")
    @Operation(summary = "【陪练师】发起提现申请 (扣减可用余额，进入冻结池)")
    public CommonResult<WithdrawRecord> applyWithdraw(@Valid @RequestBody ApplyWithdrawDTO dto) {
        // 提现人只能是当前登录的陪玩师本人
        Long currentUserId = authGuard.currentUserId();
        WithdrawRecord record = userWalletService.applyWithdraw(currentUserId, dto);
        return CommonResult.success(record, "提现申请已提交，等待平台管理员审核打款！");
    }

    @GetMapping("/withdraw/my-list")
    @Operation(summary = "【陪练师】查看我的历史提现记录")
    public CommonResult<Page<WithdrawItemVO>> getMyWithdraws(@RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = authGuard.currentUserId();
        Page<WithdrawItemVO> page = userWalletService.getWithdrawList(currentUserId, null, pageNum, pageSize);
        return CommonResult.success(page);
    }

    @GetMapping("/flow/my-list")
    @Operation(summary = "【陪练师】查看我的钱包收支明细对账流水")
    public CommonResult<Page<WalletFlow>> getMyFlows(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        Long currentUserId = authGuard.currentUserId();
        Page<WalletFlow> page = userWalletService.getWalletFlowList(currentUserId, pageNum, pageSize);
        return CommonResult.success(page);
    }

    @GetMapping("/admin/withdraw/list")
    @Operation(summary = "【管理员手机端】获取待审核打款提现申请列表")
    public CommonResult<Page<WithdrawItemVO>> getAdminWithdrawList(@RequestParam(required = false) Integer status,
                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "20") int pageSize) {
        // 提现打款审核属于平台特权操作
        authGuard.requireAdmin();
        Page<WithdrawItemVO> page = userWalletService.getWithdrawList(null, status, pageNum, pageSize);
        return CommonResult.success(page);
    }

    @PostMapping("/admin/withdraw/audit")
    @Operation(summary = "【管理员手机端】一键打款审核决策 (同意打款/驳回)")
    public CommonResult<String> auditWithdraw(@Valid @RequestBody AuditWithdrawDTO dto) {
        authGuard.requireAdmin();
        userWalletService.processWithdrawAudit(dto);
        return CommonResult.success(dto.getIsApproved() ? "已批准提现申请，资金打款成功！" : "已驳回提现申请，冻结资金已退回钱包余额！");
    }
}
