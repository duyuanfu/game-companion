package com.game.companion.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信 getPhoneNumber 手机号一键授权绑定入参")
public class BindPhoneDTO {

    @Schema(description = "button open-type='getPhoneNumber' 回调事件中的 e.detail.code (若有)")
    private String code;

    @Schema(description = "用户手动输入的真实手机号码 (用于未认证小程序或开发期直绑)")
    private String phone;
}
