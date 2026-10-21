package com.game.companion.modules.companion.service;

import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.vo.CompanionItemVO;
import com.game.companion.modules.companion.vo.CompanionWorkbenchVO;

import java.util.List;

public interface CompanionService {

    /**
     * 从数据库查询审核通过的陪玩师列表 (支持按游戏筛选)
     *
     * @param gameName       游戏品类筛选，为空或「全部」表示不过滤
     * @param currentUserId  当前登录用户ID，用于从源头屏蔽「自己」的卡片（防刷单）；未登录传 null
     */
    List<CompanionItemVO> listCompanions(String gameName, Long currentUserId);

    /**
     * 获取陪玩师支持的所有游戏服务项目与定价列表
     */
    List<CompanionSkill> getCompanionSkills(Long companionUserId);

    /**
     * 获取陪玩师工作台真实统计数据 (余额、接单状态、单数)
     */
    CompanionWorkbenchVO getWorkbenchStats(Long userId);

    /**
     * 切换真实接单状态
     */
    void toggleOnlineStatus(Long userId, boolean isOnline);

    /**
     * 客户申请成为陪练师 (支持申报多个擅长游戏技能项目)
     */
    void applyCompanion(Long userId, com.game.companion.modules.companion.dto.ApplyCompanionDTO dto);
}
