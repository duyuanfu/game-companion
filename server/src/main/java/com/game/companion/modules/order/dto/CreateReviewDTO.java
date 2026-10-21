package com.game.companion.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "提交订单评价入参")
public class CreateReviewDTO {

    @NotBlank(message = "订单号不能为空")
    @Schema(description = "订单业务流水号")
    private String orderSn;

    @NotNull(message = "综合评分不能为空")
    @Min(value = 1, message = "评分最低为1星")
    @Max(value = 5, message = "评分最高为5星")
    @Schema(description = "综合星级 (1-5星)")
    private Integer score;

    @Schema(description = "技术评分 (1-5星)")
    private Integer skillScore;

    @Schema(description = "态度评分 (1-5星)")
    private Integer attitudeScore;

    @Schema(description = "评价印象标签 (逗号分隔)")
    private String tags;

    @Schema(description = "评价留言内容")
    private String content;
}
