package com.game.companion.modules.companion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.companion.dto.CompanionSkillDTO;
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import com.game.companion.modules.companion.mapper.CompanionSkillMapper;
import com.game.companion.modules.companion.service.CompanionService;
import com.game.companion.modules.companion.vo.CompanionItemVO;
import com.game.companion.modules.companion.vo.CompanionSkillVO;
import com.game.companion.modules.companion.vo.CompanionStatusVO;
import com.game.companion.modules.companion.vo.CompanionWorkbenchVO;
import com.game.companion.modules.game.entity.GameCategory;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import com.game.companion.modules.wallet.entity.UserWallet;
import com.game.companion.modules.wallet.mapper.UserWalletMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 陪玩师接单后的履约时长上限 (分钟)。超时未完成服务，系统自动为客户全额原路退款。
     */
    @Value("${platform.order-service-timeout-minutes:60}")
    private int serviceTimeoutMinutes;

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

            // 【服务中状态】查询该陪玩师当前是否有进行中的订单(服务中2 / 待客户确认3)
            CompanionOrder servingOrder = companionOrderMapper.selectOne(
                    new LambdaQueryWrapper<CompanionOrder>()
                            .eq(CompanionOrder::getCompanionUserId, user.getId())
                            .in(CompanionOrder::getOrderStatus, 2, 3)
                            .orderByDesc(CompanionOrder::getAcceptTime)
                            .last("LIMIT 1")
            );
            boolean isServing = servingOrder != null;
            String servingStatusText = null;
            String servingRemainText = null;
            if (isServing) {
                servingStatusText = (servingOrder.getOrderStatus() != null && servingOrder.getOrderStatus() == 3)
                        ? "待客户确认" : "服务中";
                long remain = 0;
                if (servingOrder.getAcceptTime() != null) {
                    remain = java.time.Duration.between(LocalDateTime.now(),
                            servingOrder.getAcceptTime().plusMinutes(serviceTimeoutMinutes)).getSeconds();
                }
                long safe = Math.max(0, remain);
                servingRemainText = String.format("%02d:%02d", safe / 60, safe % 60);
            }

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
                    .isServing(isServing)
                    .servingStatusText(servingStatusText)
                    .servingRemainText(servingRemainText)
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
    public List<CompanionSkillVO> getCompanionSkillViews(Long companionUserId) {
        return toSkillViews(getCompanionSkills(companionUserId));
    }

    @Override
    public List<CompanionSkillVO> getMySkillViews(Long userId) {
        List<CompanionSkill> skills = companionSkillMapper.selectList(
                new LambdaQueryWrapper<CompanionSkill>()
                        .eq(CompanionSkill::getUserId, userId)
                        .orderByDesc(CompanionSkill::getStatus)
                        .orderByAsc(CompanionSkill::getPrice)
        );
        return toSkillViews(skills);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanionSkillVO saveMySkill(Long userId, Long skillId, CompanionSkillDTO dto) {
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, userId)
        );
        if (profile == null) {
            throw new ApiException("请先提交入驻申请，再维护游戏技能");
        }

        CompanionSkill skill;
        if (skillId == null) {
            skill = new CompanionSkill();
            skill.setUserId(userId);
            skill.setCompanionId(profile.getId());
            skill.setCreateTime(LocalDateTime.now());
            skill.setStatus(1);
        } else {
            skill = companionSkillMapper.selectById(skillId);
            if (skill == null || !userId.equals(skill.getUserId())) {
                throw new ApiException("技能不存在或无权修改");
            }
        }

        String previousGameName = skill.getGameName();
        String previousRankName = skill.getRankName();

        GameCategory category = gameCategoryMapper.selectOne(
                new LambdaQueryWrapper<GameCategory>().eq(GameCategory::getName, dto.getGameName().trim())
        );
        skill.setGameId(category != null ? category.getId() : 0L);
        skill.setGameName(dto.getGameName().trim());
        skill.setRankName(dto.getRankName().trim());
        skill.setPrice(dto.getPrice());
        skill.setUnit(dto.getUnit() == null || dto.getUnit().isBlank() ? "小时" : dto.getUnit().trim());
        if (dto.getRankImg() != null) {
            skill.setRankImg(dto.getRankImg());
        }
        if (dto.getStatus() != null) {
            skill.setStatus(dto.getStatus() == 0 ? 0 : 1);
        }

        // 价格、上下架可自助修改；新增技能或修改游戏/段位属于资质变更，重新进入审核
        boolean materialChange = skillId == null
                || !dto.getGameName().trim().equals(previousGameName)
                || !dto.getRankName().trim().equals(previousRankName);
        if (materialChange && profile.getAuditStatus() != null && profile.getAuditStatus() == 1) {
            profile.setAuditStatus(0);
            profile.setRejectReason(null);
            profile.setUpdateTime(LocalDateTime.now());
            companionProfileMapper.updateById(profile);
        }

        try {
            if (skill.getId() == null) {
                companionSkillMapper.insert(skill);
            } else {
                companionSkillMapper.updateById(skill);
            }
        } catch (DuplicateKeyException e) {
            throw new ApiException("同一游戏已存在技能，请直接编辑原技能");
        }
        return toSkillViews(List.of(skill)).get(0);
    }

    private List<CompanionSkillVO> toSkillViews(List<CompanionSkill> skills) {
        List<CompanionSkillVO> result = new ArrayList<>();
        if (skills == null || skills.isEmpty()) {
            return result;
        }

        // 一次性把游戏品类字典捞出来, 避免 N+1 查询
        List<GameCategory> categories = gameCategoryMapper.selectList(
                new LambdaQueryWrapper<GameCategory>().eq(GameCategory::getIsActive, 1)
        );
        // 以 id 为主键索引 (外键更可靠), 名称作为兜底 (兼容历史数据 game_id 缺失的场景)
        Map<Long, GameCategory> iconMapById = new HashMap<>();
        Map<String, GameCategory> iconMapByName = new HashMap<>();
        for (GameCategory c : categories) {
            if (c.getId() != null) {
                iconMapById.put(c.getId(), c);
            }
            if (c.getName() != null) {
                iconMapByName.put(c.getName(), c);
            }
        }

        for (CompanionSkill s : skills) {
            GameCategory category = (s.getGameId() != null) ? iconMapById.get(s.getGameId()) : null;
            if (category == null && s.getGameName() != null) {
                category = iconMapByName.get(s.getGameName());
            }
            result.add(CompanionSkillVO.builder()
                    .id(s.getId())
                    .companionId(s.getCompanionId())
                    .userId(s.getUserId())
                    .gameId(s.getGameId())
                    .gameName(s.getGameName())
                    .gameIcon(category != null ? category.getIcon() : null)
                    .rankName(s.getRankName())
                    .rankImg(s.getRankImg())
                    .price(s.getPrice())
                    .priceYuan(s.getPrice() != null ? s.getPrice() / 100.0 : 0.0)
                    .unit(s.getUnit())
                    .status(s.getStatus())
                    .build());
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
        // 【准入校验】仅提交了入驻申请（审核中/已驳回）不得进入接单工作台
        assertAuditPassed(profile);

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
        if (profile == null) {
            throw new ApiException("当前用户尚未成为认证陪练师");
        }
        // 【准入校验】未通过入驻审核不得开启接单
        assertAuditPassed(profile);
        if (profile.getIsOnline() != null) {
            profile.setIsOnline(isOnline ? 1 : 0);
            companionProfileMapper.updateById(profile);
        }
    }

    /**
     * 【准入校验】陪玩师必须通过入驻审核 (audit_status = 1) 才具备接单资格。
     * 仅提交申请（待审核）或被驳回，一律不得进入接单工作台、不得开启接单开关。
     */
    private void assertAuditPassed(CompanionProfile profile) {
        Integer auditStatus = profile.getAuditStatus();
        if (auditStatus == null || auditStatus != 1) {
            if (auditStatus != null && auditStatus == 2) {
                String reason = profile.getRejectReason();
                throw new ApiException("入驻申请未通过审核，无法进入接单工作台"
                        + (reason != null && !reason.trim().isEmpty() ? "：" + reason.trim() : "，请修改资料后重新提交"));
            }
            throw new ApiException("入驻申请审核中，请等待管理员核验通过后再进入接单工作台");
        }
    }

    @Override
    public CompanionStatusVO getMyCompanionStatus(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        int role = (user != null && user.getRole() != null) ? user.getRole() : RoleEnum.CLIENT.getCode();

        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, userId)
        );

        boolean hasProfile = profile != null;
        int auditStatus = hasProfile && profile.getAuditStatus() != null ? profile.getAuditStatus() : 0;
        // 平台管理员天然具备全部接单权限
        boolean canAcceptOrder = role == RoleEnum.ADMIN.getCode() || (hasProfile && auditStatus == 1);

        return CompanionStatusVO.builder()
                .hasProfile(hasProfile)
                .auditStatus(auditStatus)
                .rejectReason(hasProfile ? profile.getRejectReason() : null)
                .role(role)
                .canAcceptOrder(canAcceptOrder)
                .isOnline(hasProfile && profile.getIsOnline() != null ? profile.getIsOnline() : 0)
                .realName(hasProfile ? profile.getRealName() : null)
                .build();
    }

    /**
     * 判断昵称是否仍为系统自动生成的占位昵称（如「微信用户_1234」）。
     * 只有这类昵称才允许在入驻申请时被实名覆盖；用户自定义的昵称一律保留。
     */
    private boolean isAutoGeneratedNickname(String nickname) {
        if (nickname == null) {
            return true;
        }
        String trimmed = nickname.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return trimmed.startsWith("微信用户_") || trimmed.startsWith("微信用户");
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
                // 【尊重用户自定义昵称】只有昵称仍是系统自动生成的占位昵称时，才用实名覆盖；
                // 用户在个人中心主动改过的昵称必须保留，不能被入驻申请静默冲掉。
                if (isAutoGeneratedNickname(user.getNickname())) {
                    user.setNickname(dto.getRealName().trim());
                }
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
            Long gameId = (category != null) ? category.getId() : 0L;

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
