package com.game.companion.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Web 手机号注册入参")
public class RegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    @Schema(description = "11 位手机号")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度为 8~64 位")
    @Schema(description = "登录密码，至少 8 位")
    private String password;

    @Size(max = 20, message = "昵称不能超过 20 个字符")
    @Schema(description = "昵称（可选，默认自动生成）")
    private String nickname;
}
