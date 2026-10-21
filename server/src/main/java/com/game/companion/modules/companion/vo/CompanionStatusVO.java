package com.game.companion.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 【陪玩师入驻状态视图】
 *
 * <p>用于前端正确区分「未申请 / 审核中 / 已驳回 / 已通过」四种状态，
 * 避免出现「仅提交申请就能进入接单工作台」的越权体验。</p>
 */
@Data
@Builder
@Schema(description = "当前登录用户的陪玩师入驻与接单资格状态")
public class CompanionStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否已提交过入驻申请 (存在陪玩师档案)")
    private boolean hasProfile;

    @Schema(description = "入驻审核状态：0-待审核 1-已通过 2-已驳回")
    private int auditStatus;

    @Schema(description = "驳回原因 (审核未通过时返回)")
    private String rejectReason;

    @Schema(description = "当前账号角色：1-客户 2-陪玩师 9-管理员")
    private int role;

    @Schema(description = "是否具备接单资格 (审核通过或为平台管理员)")
    private boolean canAcceptOrder;

    @Schema(description = "当前接单开关状态：1-接单中 0-休息中")
    private int isOnline;

    @Schema(description = "实名 (审核通过后返回)")
    private String realName;
}
