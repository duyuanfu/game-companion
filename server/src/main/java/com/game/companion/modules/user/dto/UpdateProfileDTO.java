package com.game.companion.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户自助修改资料入参（头像 + 昵称）
 */
@Data
@Schema(description = "用户自助修改头像与昵称入参")
public class UpdateProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称长度不能超过 20 个字符")
    @Schema(description = "新昵称 (1~20 个字符)")
    private String nickname;

    @Size(max = 512, message = "头像地址过长")
    @Schema(description = "新头像地址 (http/https)；为空表示不修改头像")
    private String avatarUrl;
}
