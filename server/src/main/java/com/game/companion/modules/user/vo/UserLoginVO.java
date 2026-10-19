package com.game.companion.modules.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "用户登录响应数据")
public class UserLoginVO {

    @Schema(description = "系统用户ID")
    private Long userId;

    @Schema(description = "微信 OpenID")
    private String openid;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatarUrl;

    @Schema(description = "手机号码 (若未绑定则为 null)")
    private String phone;

    @Schema(description = "角色: 1-普通客户 2-认证陪玩师 9-超级管理员")
    private Integer role;

    @Schema(description = "Sa-Token 鉴权 Token 凭证")
    private String token;
}
