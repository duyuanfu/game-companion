package com.game.companion.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.admin.dto.AuditApplyDTO;
import com.game.companion.modules.admin.service.AdminDashboardService;
import com.game.companion.modules.admin.vo.AdminDashboardVO;
import com.game.companion.modules.admin.vo.CompanionAuditItemVO;
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import com.game.companion.modules.companion.mapper.CompanionSkillMapper;
import com.game.companion.modules.notice.service.NoticeService;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final NoticeService noticeService;
    private final CompanionProfileMapper companionProfileMapper;
    private final CompanionSkillMapper companionSkillMapper;
    private final SysUserMapper sysUserMapper;
    private final CompanionOrderMapper companionOrderMapper;

    @Override
    public Page<CompanionAuditItemVO> getPendingAudits(int pageNum, int pageSize) {
        Page<CompanionProfile> page = new Page<>(pageNum, pageSize);
        
        // 从数据库真实查询 audit_status = 0 (待审核)
        LambdaQueryWrapper<CompanionProfile> wrapper = new LambdaQueryWrapper<CompanionProfile>()
                .eq(CompanionProfile::getAuditStatus, 0)
                .orderByDesc(CompanionProfile::getCreateTime);

        Page<CompanionProfile> profilePage = companionProfileMapper.selectPage(page, wrapper);
        List<CompanionAuditItemVO> voList = new ArrayList<>();

        for (CompanionProfile p : profilePage.getRecords()) {
            SysUser user = sysUserMapper.selectById(p.getUserId());
            List<CompanionSkill> skills = companionSkillMapper.selectList(
                    new LambdaQueryWrapper<CompanionSkill>().eq(CompanionSkill::getCompanionId, p.getId())
            );
            CompanionSkill skill = skills.isEmpty() ? null : skills.get(0);

            CompanionAuditItemVO vo = CompanionAuditItemVO.builder()
                    .profileId(p.getId())
                    .userId(p.getUserId())
                    .nickname(user != null ? user.getNickname() : "申请人")
                    .avatarUrl(user != null ? user.getAvatarUrl() : "")
                    .phone(user != null ? user.getPhone() : "")
                    .voiceUrl(p.getVoiceUrl())
                    .voiceDuration(p.getVoiceDuration())
                    .rankName(skill != null ? (skill.getGameName() + " - " + skill.getRankName()) : "暂未提交")
                    .rankImg(skill != null ? skill.getRankImg() : "")
                    .bio(p.getBio())
                    .applyTime(p.getCreateTime())
                    .build();
            voList.add(vo);
        }

        Page<CompanionAuditItemVO> resultPage = new Page<>(pageNum, pageSize, profilePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAudit(AuditApplyDTO dto) {
        CompanionProfile profile = companionProfileMapper.selectById(dto.getProfileId());
        if (profile == null) {
            throw new ApiException("未找到该入驻申请记录");
        }

        if (Boolean.FALSE.equals(dto.getIsApproved()) && (dto.getRejectReason() == null || dto.getRejectReason().trim().isEmpty())) {
            throw new ApiException("驳回时必须选择或填写驳回原因");
        }

        // 真实更新数据库审核状态
        profile.setAuditStatus(dto.getIsApproved() ? 1 : 2);
        profile.setRejectReason(dto.getRejectReason());
        companionProfileMapper.updateById(profile);

        // 如果通过，将 sys_user 升级为陪玩师角色
        if (Boolean.TRUE.equals(dto.getIsApproved())) {
            SysUser user = sysUserMapper.selectById(profile.getUserId());
            if (user != null) {
                user.setRole(RoleEnum.COMPANION.getCode());
                sysUserMapper.updateById(user);
            }
        }

        // 触发微信通知
        noticeService.sendAuditResultNotice(profile.getUserId(), dto.getIsApproved(), dto.getRejectReason());
    }

    @Override
    public AdminDashboardVO getDashboardSummary() {
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 真实统计数据库今天生成的订单
        List<CompanionOrder> todayOrders = companionOrderMapper.selectList(
                new LambdaQueryWrapper<CompanionOrder>()
                        .ge(CompanionOrder::getCreateTime, startOfDay)
                        .le(CompanionOrder::getCreateTime, endOfDay)
        );

        int totalCount = todayOrders.size();
        int totalAmount = todayOrders.stream().mapToInt(CompanionOrder::getTotalAmount).sum();
        int totalPlatformFee = todayOrders.stream().mapToInt(CompanionOrder::getPlatformFee).sum();

        // 统计在线陪玩师数量
        Long onlineCount = companionProfileMapper.selectCount(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getAuditStatus, 1)
                        .eq(CompanionProfile::getIsOnline, 1)
        );

        // 统计待审核人数
        Long pendingCount = companionProfileMapper.selectCount(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getAuditStatus, 0)
        );

        return AdminDashboardVO.builder()
                .todayOrderCount(totalCount)
                .todayTotalAmount(totalAmount)
                .todayPlatformFee(totalPlatformFee)
                .onlineCompanionCount(onlineCount.intValue())
                .pendingAuditCount(pendingCount.intValue())
                .build();
    }
}
