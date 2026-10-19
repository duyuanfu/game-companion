package com.game.companion.modules.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.companion.modules.admin.dto.AuditApplyDTO;
import com.game.companion.modules.admin.vo.AdminDashboardVO;
import com.game.companion.modules.admin.vo.CompanionAuditItemVO;

public interface AdminDashboardService {

    Page<CompanionAuditItemVO> getPendingAudits(int pageNum, int pageSize);

    void processAudit(AuditApplyDTO dto);

    AdminDashboardVO getDashboardSummary();
}
