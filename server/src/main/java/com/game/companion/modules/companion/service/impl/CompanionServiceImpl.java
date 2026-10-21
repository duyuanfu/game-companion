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
    private final com.game.companion.modules.order.mapper.OrderReviewMapper orderReviewMapper;
    private final com.game.companion.modules.game.mapper.GameCategoryMapper gameCategoryMapper;

    @Override
    public List<CompanionItemVO> listCompanions(String gameName, Long currentUserId) {
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

            // 【防刷单】从后端源头屏蔽自己的陪玩师卡片
            if (currentUserId != null && currentUserId.equals(user.getId())) {
                continue;
            }

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

            // 动态真实统计：综合评分联动 + 真实完成接单量
            java.math.BigDecimal realAvgRating = orderReviewMapper.calculateAverageRating(user.getId());
            java.math.BigDecimal finalRating = (realAvgRating != null) 
                    ? realAvgRating.setScale(1, java.math.RoundingMode.HALF_UP) 
                    : (p.getRating() != null ? p.getRating() : new java.math.BigDecimal("5.0"));

            // 【单一数据源】累计接单数直接以「已完结订单」的真实条数为准，
            // 避免与 companion_profile.order_count 缓存字段重复累加导致数字翻倍
            Long realFinishedOrders = companionOrderMapper.selectCount(
                    new LambdaQueryWrapper<CompanionOrder>()
                            .eq(CompanionOrder::getCompanionUserId, user.getId())
                            .eq(CompanionOrder::getOrderStatus, 4)
            );
            int totalOrderCount = realFinishedOrders == null ? 0 : realFinishedOrders.intValue();

            // 统计该陪练师在 order_review 中真实收到的好评数
            Long reviewCount = orderReviewMapper.selectCount(
                    new LambdaQueryWrapper<com.game.companion.modules.order.entity.OrderReview>()
                            .eq(com.game.companion.modules.order.entity.OrderReview::getCompanionUserId, user.getId())
            );

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
                    .rating(finalRating)
                    .orderCount(totalOrderCount)
                    .reviewCount(reviewCount != null ? reviewCount.intValue() : 0)
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

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void applyCompanion(Long userId, com.game.companion.modules.companion.dto.ApplyCompanionDTO dto) {
        if (dto.getSkills() == null || dto.getSkills().isEmpty()) {
            throw new ApiException("申请入驻必须至少添加一个擅长游戏服务项目");
        }

        // 1. 同步更新用户基础实名与手机号
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            if (dto.getRealName() != null && !dto.getRealName().trim().isEmpty()) {
                user.setNickname(dto.getRealName().trim());
            }
            if (dto.getPhone() != null && !dto.getPhone().trim().isEmpty()) {
                user.setPhone(dto.getPhone().trim());
            }
            user.setUpdateTime(LocalDateTime.now());
            sysUserMapper.updateById(user);
        }

        // 2. 创建或更新陪练师资料主档 (重置为待审核状态 0)
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, userId)
        );
        if (profile == null) {
            profile = new CompanionProfile();
            profile.setUserId(userId);
            profile.setRating(new java.math.BigDecimal("5.0"));
            profile.setOrderCount(0);
            profile.setIsOnline(1);
            profile.setCreateTime(LocalDateTime.now());
        }
        profile.setRealName(dto.getRealName());
        profile.setBio(dto.getBio());
        // 【彻底移除假音色兜底】未真实录制上传原声时一律留空，由前端做无音频降级展示
        profile.setVoiceUrl(dto.getVoiceUrl() != null && !dto.getVoiceUrl().trim().isEmpty()
                ? dto.getVoiceUrl().trim() : null);
        profile.setVoiceDuration(dto.getVoiceDuration() != null && dto.getVoiceDuration() > 0
                ? dto.getVoiceDuration() : 0);
        profile.setAuditStatus(0); // 待审核
        profile.setRejectReason(null);
        profile.setUpdateTime(LocalDateTime.now());

        if (profile.getId() == null) {
            companionProfileMapper.insert(profile);
        } else {
            companionProfileMapper.updateById(profile);
        }

        // 3. 多游戏技能资质批量入库 (支持同时申报多个游戏)
        // 先清理该陪练历史旧技能记录，重新批量插入最新申报技能
        companionSkillMapper.delete(
                new LambdaQueryWrapper<CompanionSkill>().eq(CompanionSkill::getCompanionId, profile.getId())
        );

        for (var skillItem : dto.getSkills()) {
            // 解析游戏品类ID
            var category = gameCategoryMapper.selectOne(
                    new LambdaQueryWrapper<com.game.companion.modules.game.entity.GameCategory>()
                            .eq(com.game.companion.modules.game.entity.GameCategory::getName, skillItem.getGameName())
            );
            Long gameId = category != null ? category.getId() : 1L;

            int priceInCents = 2500;
            if (skillItem.getPrice() != null && skillItem.getPrice() > 0) {
                // 【单位显式化】priceUnit 缺省按「元」处理，彻底消除原先 <500 阈值猜测导致的价格放大 100 倍的缺陷
                boolean priceInYuan = !("FEN".equalsIgnoreCase(skillItem.getPriceUnit()));
                long cents = priceInYuan
                        ? (long) skillItem.getPrice() * 100L
                        : (long) skillItem.getPrice();
                // 合法区间: 0.01 元 ~ 100000 元
                if (cents < 1L || cents > 10_000_000L) {
                    throw new com.game.companion.common.exception.ApiException(
                            "【游戏: " + skillItem.getGameName() + "】单价设置超出合法范围，请检查后重新提交");
                }
                priceInCents = (int) cents;
            }

            CompanionSkill skill = new CompanionSkill();
            skill.setCompanionId(profile.getId());
            skill.setUserId(userId);
            skill.setGameId(gameId);
            skill.setGameName(skillItem.getGameName());
            skill.setRankName(skillItem.getRankName());
            skill.setRankImg(skillItem.getRankImg());
            skill.setPrice(priceInCents);
            skill.setUnit(skillItem.getUnit() != null ? skillItem.getUnit() : "小时");
            skill.setStatus(1); // 默认上架
            skill.setCreateTime(LocalDateTime.now());
            companionSkillMapper.insert(skill);
        }
    }
}
