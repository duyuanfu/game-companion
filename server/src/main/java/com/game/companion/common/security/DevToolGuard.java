package com.game.companion.common.security;

import com.game.companion.common.api.ResultCode;
import com.game.companion.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 【开发期工具守卫】
 *
 * <p>诊断接口、身份切换等特权接口只能在开发环境开启，
 * 生产环境（{@code platform.dev-tools-enabled=false}）一律拒绝，避免形成后门。</p>
 */
@Component
public class DevToolGuard {

    @Value("${platform.dev-tools-enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 开发期工具未开启时，直接拒绝访问。
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }
}
