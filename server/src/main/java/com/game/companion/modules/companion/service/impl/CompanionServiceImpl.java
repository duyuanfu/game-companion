package com.game.companion.modules.companion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import com.game.companion.modules.companion.mapper.CompanionSkillMapper;
import com.game.companion.modules.companion.service.CompanionService;
import com.game.companion.modules.companion.vo.CompanionItemVO;
import com.game.companion.modules.companion.vo.CompanionWorkbenchVO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import com.game.companion.modules.wallet.entity.UserWallet;
import com.game.companion.modules.wallet.mapper.UserWalletMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanionServiceImpl implements CompanionService {

    private final CompanionProfileMapper companionProfileMapper;
    private final CompanionSkillMapper companionSkillMapper;
    private final SysUserMapper sysUserMapper;
    private final UserWalletMapper userWalletMapper;
    private final CompanionOrderMapper companionOrderMapper;

    @Override
    public List<CompanionItemVO> listCompanions(String gameName) {
        List<CompanionProfile> profiles = companionProfileMapper.selectList(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getAuditStatus, 1)
                        .orderByDesc(CompanionProfile::getIsOnline)
                        .orderByDesc(CompanionProfile::getOrderCount)
        );

        List<CompanionItemVO> result = new ArrayList<>();

        for (CompanionProfile p : profiles) {
            SysUser user = sysUserMapper.selectById(p.getUserId());
            if (user == null) continue;

            LambdaQueryWrapper<CompanionSkill> skillWrapper = new LambdaQueryWrapper<CompanionSkill>()
                    .eq(CompanionSkill::getCompanionId, p.getId())
                    .eq(CompanionSkill::getStatus, 1);
            if (gameName != null && !gameName.trim().isEmpty() && !"全部".equals(gameName)) {
                skillWrapper.eq(CompanionSkill::getGameName, gameName);
            }

            List<CompanionSkill> skills = companionSkillMapper.selectList(skillWrapper);
            if (skills.isEmpty()) {
                if (gameName != null && !gameName.trim().isEmpty() && !"全部".equals(gameName)) {
                    continue;
                }
            }

            CompanionSkill primarySkill = skills.isEmpty() ? null : skills.get(0);

            CompanionItemVO vo = CompanionItemVO.builder()
                    .id(p.getId())
                    .userId(user.getId())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatarUrl())
                    .game(primarySkill != null ? primarySkill.getGameName() : "王者荣耀")
                    .rank(primarySkill != null ? primarySkill.getRankName() : "最强王者")
                    .price(primarySkill != null ? primarySkill.getPrice() / 100 : 25)
                    .unit(primarySkill != null ? primarySkill.getUnit() : "小时")
                    .skillId(primarySkill != null ? primarySkill.getId() : 1L)
                    .isOnline(p.getIsOnline() == 1)
                    .voiceDuration(p.getVoiceDuration())
                    .voiceUrl(p.getVoiceUrl())
                    .rating(p.getRating())
                    .orderCount(p.getOrderCount())
                    .bio(p.getBio())
                    .build();

            result.add(vo);
        }

        return result;
    }

    @Override
    public List<CompanionSkill> getCompanionSkills(Long companionUserId) {
        return companionSkillMapper.selectList(
                new LambdaQueryWrapper<CompanionSkill>()
                        .eq(CompanionSkill::getUserId, companionUserId)
                        .eq(CompanionSkill::getStatus, 1)
                        .orderByAsc(CompanionSkill::getPrice)
        );
    }

    @Override
    public CompanionWorkbenchVO getWorkbenchStats(Long userId) {
        // 1. 查询陪玩档案
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, userId)
        );
        if (profile == null) {
            throw new ApiException("当前用户尚未成为认证陪练师");
        }

        // 2. 查询真实钱包
        UserWallet wallet = userWalletMapper.selectOne(
                new LambdaQueryWrapper<UserWallet>().eq(UserWallet::getUserId, userId)
        );
        int balance = wallet != null ? wallet.getBalance() : 0;
        int totalIncome = wallet != null ? wallet.getTotalIncome() : 0;

        // 3. 统计本月完成单数
        LocalDateTime startOfMonth = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN);
        Long finishCount = companionOrderMapper.selectCount(
                new LambdaQueryWrapper<CompanionOrder>()
                        .eq(CompanionOrder::getCompanionUserId, userId)
                        .eq(CompanionOrder::getOrderStatus, 4)
                        .ge(CompanionOrder::getCompleteTime, startOfMonth)
        );

        // 4. 查询主技能
        List<CompanionSkill> skills = companionSkillMapper.selectList(
                new LambdaQueryWrapper<CompanionSkill>().eq(CompanionSkill::getCompanionId, profile.getId())
        );
        CompanionSkill primarySkill = skills.isEmpty() ? null : skills.get(0);

        return CompanionWorkbenchVO.builder()
                .isOnline(profile.getIsOnline() == 1)
                .balance(balance)
                .totalIncome(totalIncome)
                .monthFinishCount(finishCount.intValue())
                .gameName(primarySkill != null ? primarySkill.getGameName() : "王者荣耀")
                .rankName(primarySkill != null ? primarySkill.getRankName() : "最强王者 35星")
                .price(primarySkill != null ? primarySkill.getPrice() : 2500)
                .unit(primarySkill != null ? primarySkill.getUnit() : "小时")
                .voiceDuration(profile.getVoiceDuration())
                .build();
    }

    @Override
    public void toggleOnlineStatus(Long userId, boolean isOnline) {
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, userId)
        );
        if (profile != null) {
            profile.setIsOnline(isOnline ? 1 : 0);
            companionProfileMapper.updateById(profile);
        }
    }
}
