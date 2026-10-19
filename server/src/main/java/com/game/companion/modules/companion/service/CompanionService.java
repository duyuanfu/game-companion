package com.game.companion.modules.companion.service;

import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.vo.CompanionItemVO;
import com.game.companion.modules.companion.vo.CompanionWorkbenchVO;

import java.util.List;

public interface CompanionService {

    /**
     * 从数据库查询审核通过的陪玩师列表 (支持按游戏筛选)
     */
    List<CompanionItemVO> listCompanions(String gameName);

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
}
