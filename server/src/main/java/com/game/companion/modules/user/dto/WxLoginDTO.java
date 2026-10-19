package com.game.companion.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信小程序静默登录入参")
public class WxLoginDTO {

    @NotBlank(message = "wx.login 授权 code 不能为空")
    @Schema(description = "小程序端 wx.login 获得的临时登录凭证 code")
    private String code;

    @Schema(description = "设备类型: simulator(电脑模拟器) / phone(手机真机)")
    private String deviceType;
}
