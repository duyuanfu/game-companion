package com.game.companion.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
@Schema(description = "系统用户基础实体")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "微信小程序 OpenID")
    private String openid;

    @Schema(description = "微信开放平台 UnionID")
    private String unionid;

    @Schema(description = "微信公众号服务号 OpenID")
    private String mpOpenid;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "头像地址")
    private String avatarUrl;

    @Schema(description = "手机号码 (用于接单短信兜底提醒)")
    private String phone;

    @Schema(description = "性别: 0-未知 1-男 2-女")
    private Integer gender;

    @Schema(description = "角色: 1-普通客户 2-陪玩师 9-超级管理员")
    private Integer role;

    @Schema(description = "账号状态: 0-已封禁 1-正常")
    private Integer status;

    @TableLogic
    @Schema(description = "逻辑删除: 0-正常 1-已删除")
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
