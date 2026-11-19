package com.game.companion.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Web 手机号密码登录入参")
public class PasswordLoginDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    @Schema(description = "11 位手机号")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度为 8~64 位")
    @Schema(description = "登录密码")
    private String password;
}
