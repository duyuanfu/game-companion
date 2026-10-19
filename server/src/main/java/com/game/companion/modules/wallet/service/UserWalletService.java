package com.game.companion.modules.wallet.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.game.companion.modules.wallet.dto.ApplyWithdrawDTO;
import com.game.companion.modules.wallet.dto.AuditWithdrawDTO;
import com.game.companion.modules.wallet.entity.UserWallet;
import com.game.companion.modules.wallet.entity.WalletFlow;
import com.game.companion.modules.wallet.entity.WithdrawRecord;
import com.game.companion.modules.wallet.vo.WithdrawItemVO;

public interface UserWalletService extends IService<UserWallet> {

    /**
     * 陪玩师申请提现 (资金进入冻结池)
     */
    WithdrawRecord applyWithdraw(Long userId, ApplyWithdrawDTO dto);

    /**
     * 超级管理员审核打款 (调用微信商家转账到零钱/Mock)
     */
    void processWithdrawAudit(AuditWithdrawDTO dto);

    /**
     * 分页查询提现申请列表 (支持管理员查全部、陪玩师查自己)
     */
    Page<WithdrawItemVO> getWithdrawList(Long userId, Integer status, int pageNum, int pageSize);

    /**
     * 分页查询钱包资金明细流水
     */
    Page<WalletFlow> getWalletFlowList(Long userId, int pageNum, int pageSize);
}
