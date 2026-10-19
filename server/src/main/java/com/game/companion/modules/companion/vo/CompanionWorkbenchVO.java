package com.game.companion.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "陪玩师工作台统计数据VO")
public class CompanionWorkbenchVO {

    @Schema(description = "是否正在接单中")
    private Boolean isOnline;

    @Schema(description = "可提现余额 (分)")
    private Integer balance;

    @Schema(description = "累计总收益 (分)")
    private Integer totalIncome;

    @Schema(description = "本月已完成单数")
    private Integer monthFinishCount;

    @Schema(description = "主推游戏名称")
    private String gameName;

    @Schema(description = "当前段位")
    private String rankName;

    @Schema(description = "当前服务定价 (分)")
    private Integer price;

    @Schema(description = "计价单位")
    private String unit;

    @Schema(description = "录音试听时长 (秒)")
    private Integer voiceDuration;
}
