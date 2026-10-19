package com.game.companion.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信 getPhoneNumber 手机号一键授权绑定入参")
public class BindPhoneDTO {

    @NotBlank(message = "微信手机号授权动态 code 不能为空")
    @Schema(description = "button open-type='getPhoneNumber' 回调事件中的 e.detail.code")
    private String code;

    @Schema(description = "备用/透传手机号 (用于开发测试对齐)")
    private String phone;
}
