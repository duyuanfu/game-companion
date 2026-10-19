package com.game.companion.modules.wallet.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import com.game.companion.modules.wallet.dto.ApplyWithdrawDTO;
import com.game.companion.modules.wallet.dto.AuditWithdrawDTO;
import com.game.companion.modules.wallet.entity.UserWallet;
import com.game.companion.modules.wallet.entity.WalletFlow;
import com.game.companion.modules.wallet.entity.WithdrawRecord;
import com.game.companion.modules.wallet.mapper.UserWalletMapper;
import com.game.companion.modules.wallet.mapper.WalletFlowMapper;
import com.game.companion.modules.wallet.mapper.WithdrawRecordMapper;
import com.game.companion.modules.wallet.service.UserWalletService;
import com.game.companion.modules.wallet.vo.WithdrawItemVO;
import com.game.companion.payment.PaymentService;
import com.game.companion.payment.PaymentStrategyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWalletServiceImpl extends ServiceImpl<UserWalletMapper, UserWallet> implements UserWalletService {

    private final UserWalletMapper userWalletMapper;
    private final WalletFlowMapper walletFlowMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final PaymentStrategyContext paymentStrategyContext;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRecord applyWithdraw(Long userId, ApplyWithdrawDTO dto) {
        // 1. 查询钱包
        UserWallet wallet = userWalletMapper.selectOne(
                new LambdaQueryWrapper<UserWallet>().eq(UserWallet::getUserId, userId)
        );
        if (wallet == null || wallet.getBalance() < dto.getAmount()) {
            throw new ApiException("可提现余额不足！当前可提现余额为: ¥" + (wallet != null ? (wallet.getBalance() / 100.0) : 0));
        }

        int withdrawAmount = dto.getAmount();
        int fee = 0; // 手续费（目前免手续费，可根据需要设置比例）
        int actualAmount = withdrawAmount - fee;
        int beforeBalance = wallet.getBalance();

        // 2. 扣减可提现余额，资金划入冻结池
        wallet.setBalance(beforeBalance - withdrawAmount);
        wallet.setFrozenBalance(wallet.getFrozenBalance() + withdrawAmount);
        wallet.setUpdateTime(LocalDateTime.now());
        userWalletMapper.updateById(wallet);

        // 3. 生成提现流水记录
        String withdrawSn = "TX_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4);
        WithdrawRecord record = new WithdrawRecord();
        record.setWithdrawSn(withdrawSn);
        record.setUserId(userId);
        record.setAmount(withdrawAmount);
        record.setFee(fee);
        record.setActualAmount(actualAmount);
        record.setChannel(paymentStrategyContext.getService().getChannelType());
        record.setStatus(0); // 0-待审核
        record.setCreateTime(LocalDateTime.now());
        withdrawRecordMapper.insert(record);

        // 4. 记录资金流水: 2-发起提现冻结
        WalletFlow flow = new WalletFlow();
        flow.setFlowSn("FLOW_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4));
        flow.setUserId(userId);
        flow.setType(2); // 2-提现冻结
        flow.setAmount(-withdrawAmount);
        flow.setBeforeBalance(beforeBalance);
        flow.setAfterBalance(wallet.getBalance());
        flow.setOrderSn(withdrawSn);
        flow.setRemark("申请提现扣除锁定 (待管理员审批打款)");
        flow.setCreateTime(LocalDateTime.now());
        walletFlowMapper.insert(flow);

        log.info("【陪玩师申请提现成功】用户ID={}, 申请金额={}分, 冻结中金额={}分, 剩余可用余额={}分",
                userId, withdrawAmount, wallet.getFrozenBalance(), wallet.getBalance());

        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processWithdrawAudit(AuditWithdrawDTO dto) {
        WithdrawRecord record = withdrawRecordMapper.selectById(dto.getWithdrawId());
        if (record == null) {
            throw new ApiException("未找到该提现申请记录");
        }
        if (record.getStatus() != 0) {
            throw new ApiException("该申请已处理完毕，请勿重复操作");
        }

        Long userId = record.getUserId();
        UserWallet wallet = userWalletMapper.selectOne(
                new LambdaQueryWrapper<UserWallet>().eq(UserWallet::getUserId, userId)
        );
        if (wallet == null) {
            throw new ApiException("对应钱包账户不存在");
        }

        SysUser user = sysUserMapper.selectById(userId);
        String openid = (user != null && user.getOpenid() != null) ? user.getOpenid() : "mock_openid";
        String realName = (user != null && user.getNickname() != null) ? user.getNickname() : "陪练师";

        if (Boolean.TRUE.equals(dto.getIsApproved())) {
            // ============= 审核通过：调用支付网关打款 (商家转账到零钱 / Mock) =============
            PaymentService paymentService = paymentStrategyContext.getService();
            String batchId = paymentService.transferToUser(
                    record.getWithdrawSn(),
                    openid,
                    realName,
                    record.getActualAmount(),
                    "开黑陪练平台收益提现结算"
            );

            // 扣除冻结资金
            wallet.setFrozenBalance(Math.max(0, wallet.getFrozenBalance() - record.getAmount()));
            wallet.setUpdateTime(LocalDateTime.now());
            userWalletMapper.updateById(wallet);

            // 提现记录状态更新为成功
            record.setStatus(2); // 2-提现成功
            record.setWechatBatchId(batchId);
            record.setFinishTime(LocalDateTime.now());
            withdrawRecordMapper.updateById(record);

            // 流水记录: 3-提现成功扣除
            WalletFlow flow = new WalletFlow();
            flow.setFlowSn("FLOW_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4));
            flow.setUserId(userId);
            flow.setType(3); // 3-提现扣除
            flow.setAmount(0); // 余额之前已扣，此处冻结资金正式划转完成
            flow.setBeforeBalance(wallet.getBalance());
            flow.setAfterBalance(wallet.getBalance());
            flow.setOrderSn(record.getWithdrawSn());
            flow.setRemark("提现打款成功到账 (批次号: " + batchId + ")");
            flow.setCreateTime(LocalDateTime.now());
            walletFlowMapper.insert(flow);

            log.info("【管理员审核提现通过并打款成功】提现单号={}, 用户={}, 金额={}分, 打款批次号={}",
                    record.getWithdrawSn(), realName, record.getActualAmount(), batchId);
        } else {
            // ============= 审核驳回：解冻资金退还到 balance =============
            if (dto.getRejectReason() == null || dto.getRejectReason().trim().isEmpty()) {
                throw new ApiException("驳回时必须填写驳回原因");
            }

            int beforeBalance = wallet.getBalance();
            wallet.setFrozenBalance(Math.max(0, wallet.getFrozenBalance() - record.getAmount()));
            wallet.setBalance(beforeBalance + record.getAmount());
            wallet.setUpdateTime(LocalDateTime.now());
            userWalletMapper.updateById(wallet);

            // 提现记录状态更新为驳回
            record.setStatus(3); // 3-已驳回
            record.setFailReason(dto.getRejectReason());
            record.setFinishTime(LocalDateTime.now());
            withdrawRecordMapper.updateById(record);

            // 流水记录: 4-提现驳回解冻
            WalletFlow flow = new WalletFlow();
            flow.setFlowSn("FLOW_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4));
            flow.setUserId(userId);
            flow.setType(4); // 4-解冻返还
            flow.setAmount(record.getAmount());
            flow.setBeforeBalance(beforeBalance);
            flow.setAfterBalance(wallet.getBalance());
            flow.setOrderSn(record.getWithdrawSn());
            flow.setRemark("提现申请被驳回，资金解冻退回钱包 (原因: " + dto.getRejectReason() + ")");
            flow.setCreateTime(LocalDateTime.now());
            walletFlowMapper.insert(flow);

            log.info("【管理员审核驳回提现】提现单号={}, 解冻返还金额={}分, 驳回原因={}",
                    record.getWithdrawSn(), record.getAmount(), dto.getRejectReason());
        }
    }

    @Override
    public Page<WithdrawItemVO> getWithdrawList(Long userId, Integer status, int pageNum, int pageSize) {
        Page<WithdrawRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<>();

        if (userId != null && userId > 0) {
            wrapper.eq(WithdrawRecord::getUserId, userId);
        }
        if (status != null && status >= 0) {
            wrapper.eq(WithdrawRecord::getStatus, status);
        }
        wrapper.orderByDesc(WithdrawRecord::getCreateTime);

        Page<WithdrawRecord> recordPage = withdrawRecordMapper.selectPage(page, wrapper);
        List<WithdrawItemVO> voList = new ArrayList<>();

        for (WithdrawRecord r : recordPage.getRecords()) {
            SysUser user = sysUserMapper.selectById(r.getUserId());
            voList.add(WithdrawItemVO.builder()
                    .id(r.getId())
                    .withdrawSn(r.getWithdrawSn())
                    .userId(r.getUserId())
                    .nickname(user != null ? user.getNickname() : "陪练师")
                    .avatarUrl(user != null ? user.getAvatarUrl() : "")
                    .phone(user != null ? user.getPhone() : "")
                    .amount(r.getAmount())
                    .fee(r.getFee())
                    .actualAmount(r.getActualAmount())
                    .status(r.getStatus())
                    .wechatBatchId(r.getWechatBatchId())
                    .failReason(r.getFailReason())
                    .createTime(r.getCreateTime())
                    .finishTime(r.getFinishTime())
                    .build());
        }

        Page<WithdrawItemVO> resultPage = new Page<>(pageNum, pageSize, recordPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public Page<WalletFlow> getWalletFlowList(Long userId, int pageNum, int pageSize) {
        Page<WalletFlow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WalletFlow> wrapper = new LambdaQueryWrapper<WalletFlow>()
                .eq(WalletFlow::getUserId, userId)
                .orderByDesc(WalletFlow::getCreateTime);
        return walletFlowMapper.selectPage(page, wrapper);
    }
}
