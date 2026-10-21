package com.game.companion.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.game.companion.common.api.ResultCode;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【全局身份与角色守卫】
 *
 * <p>安全铁律：所有对外接口的身份一律以「当前登录会话 (Sa-Token)」为准，
 * 严禁任何形式的客户端参数覆盖（如 userId / clientUserId / companionUserId / adminUserId）。
 * 历史上这些参数可被任意伪造，导致越权接单、越权结单、越权改配置等严重风险。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthGuard {

    private final SysUserMapper sysUserMapper;

    /**
     * 获取当前登录用户ID；未登录或会话失效直接抛出 401。
     */
    public Long currentUserId() {
        if (!StpUtil.isLogin()) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前登录用户ID；未登录返回 null（仅用于「可选登录态」的公开接口）。
     */
    public Long currentUserIdOrNull() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    }

    /**
     * 获取当前登录用户的完整档案（含实时角色），未登录抛 401。
     */
    public SysUser currentUser() {
        Long userId = currentUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(ResultCode.USER_NOT_EXIST);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ApiException(ResultCode.USER_DISABLED);
        }
        return user;
    }

    /**
     * 获取当前登录用户的实时角色编码（1-客户 2-陪玩师 9-管理员）。
     */
    public int currentRole() {
        SysUser user = currentUser();
        return user.getRole() == null ? RoleEnum.CLIENT.getCode() : user.getRole();
    }

    /**
     * 平台管理员专属校验，非管理员一律 403。
     */
    public void requireAdmin() {
        if (currentRole() != RoleEnum.ADMIN.getCode()) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 陪玩师（或管理员）专属校验。
     */
    public void requireCompanion() {
        int role = currentRole();
        if (role != RoleEnum.COMPANION.getCode() && role != RoleEnum.ADMIN.getCode()) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 断言当前登录用户就是目标用户（用于「只能操作自己数据」的场景）。
     */
    public void requireSelf(Long targetUserId) {
        Long current = currentUserId();
        if (targetUserId == null || !current.equals(targetUserId)) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }
}
