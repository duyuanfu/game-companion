package com.game.companion.common.config;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import com.game.companion.modules.wallet.entity.UserWallet;
import com.game.companion.modules.wallet.mapper.UserWalletMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 开发环境演示账号引导器。
 *
 * <p>仅在 dev profile 且 app.demo-accounts.enabled=true 时执行。
 * 启动时确保以下账号存在，并同时绑定快捷登录使用的 mock OpenID：</p>
 * <ul>
 *   <li>客户：13900000101 / Demo123456</li>
 *   <li>陪练：13900000102 / Demo123456</li>
 *   <li>管理员：13900000109 / Admin123456</li>
 * </ul>
 * <p>因此快捷登录与手机号密码登录会进入同一个账号。生产环境不会加载本类。</p>
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DemoAccountBootstrapRunner implements ApplicationRunner {

    private static final String CLIENT_PHONE = "13900000101";
    private static final String COMPANION_PHONE = "13900000102";
    private static final String ADMIN_PHONE = "13900000109";
    private static final String DEMO_PASSWORD = "Demo123456";
    private static final String ADMIN_PASSWORD = "Admin123456";
    private static final String MOCK_OPENID_PREFIX = "dev_openid_";

    private final SysUserMapper sysUserMapper;
    private final UserWalletMapper userWalletMapper;

    @Value("${app.demo-accounts.enabled:false}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        ensureAccount("mock_web_client", CLIENT_PHONE, "演示客户", 1, DEMO_PASSWORD);
        ensureAccount("mock_web_companion", COMPANION_PHONE, "演示陪练", 2, DEMO_PASSWORD);
        ensureAccount("mock_web_admin", ADMIN_PHONE, "演示管理员", 9, ADMIN_PASSWORD);
        log.info("【演示账号】已就绪：客户 {}、陪练 {}、管理员 {}（仅开发环境）",
                maskPhone(CLIENT_PHONE), maskPhone(COMPANION_PHONE), maskPhone(ADMIN_PHONE));
    }

    private void ensureAccount(String mockCode, String phone, String nickname, int role, String password) {
        String openid = MOCK_OPENID_PREFIX + SecureUtil.md5(mockCode).substring(0, 16);
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenid, openid), false
        );
        SysUser ownerByPhone = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone), false
        );
        if (ownerByPhone != null && (user == null || !ownerByPhone.getId().equals(user.getId()))) {
            log.warn("【演示账号】手机号 {} 已被 userId={} 占用，跳过 {}", maskPhone(phone), ownerByPhone.getId(), nickname);
            return;
        }

        if (user == null) {
            SysUser created = new SysUser();
            created.setOpenid(openid);
            created.setNickname(nickname);
            created.setPhone(phone);
            created.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
            created.setGender(0);
            created.setRole(role);
            created.setStatus(1);
            created.setDeleted(0);
            created.setCreateTime(LocalDateTime.now());
            created.setUpdateTime(LocalDateTime.now());
            sysUserMapper.insert(created);
            ensureWallet(created.getId());
            log.info("【演示账号】已创建 {}，手机号 {}，角色 {}", nickname, maskPhone(phone), role);
            return;
        }

        boolean changed = false;
        if (!phone.equals(user.getPhone())) {
            user.setPhone(phone);
            changed = true;
        }
        // 演示账号始终恢复为文档中的固定密码，避免被改后文档失效
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        changed = true;
        if (user.getRole() == null || user.getRole() != role) {
            user.setRole(role);
            changed = true;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            user.setStatus(1);
            changed = true;
        }
        // 演示账号统一昵称，便于在消息、订单等页面快速识别
        if (!nickname.equals(user.getNickname())) {
            user.setNickname(nickname);
            changed = true;
        }
        if (changed) {
            user.setUpdateTime(LocalDateTime.now());
            sysUserMapper.updateById(user);
            log.info("【演示账号】已补齐 {}（userId={}）", nickname, user.getId());
        }
        ensureWallet(user.getId());
    }

    private void ensureWallet(Long userId) {
        UserWallet wallet = userWalletMapper.selectOne(
                new LambdaQueryWrapper<UserWallet>().eq(UserWallet::getUserId, userId), false
        );
        if (wallet != null) {
            return;
        }
        UserWallet created = new UserWallet();
        created.setUserId(userId);
        created.setBalance(0);
        created.setFrozenBalance(0);
        created.setTotalIncome(0);
        created.setVersion(0);
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        userWalletMapper.insert(created);
    }

    private String maskPhone(String value) {
        if (value == null || value.length() < 7) {
            return "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
