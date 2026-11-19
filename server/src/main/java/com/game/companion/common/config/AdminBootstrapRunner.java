package com.game.companion.common.config;

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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 生产管理员引导器。
 *
 * <p>仅在显式配置 BOOTSTRAP_ADMIN_PHONE / BOOTSTRAP_ADMIN_PASSWORD 时执行：
 * 如果系统还没有管理员，则创建首个管理员；如果手机号已存在，则补齐密码并提升为管理员。
 * 执行后应从部署环境中移除这两个变量，避免长期保留。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final UserWalletMapper userWalletMapper;

    @Value("${app.bootstrap-admin.phone:}")
    private String phone;

    @Value("${app.bootstrap-admin.password:}")
    private String password;

    @Value("${app.bootstrap-admin.nickname:平台管理员}")
    private String nickname;

    @Override
    public void run(ApplicationArguments args) {
        if (phone == null || phone.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return;
        }
        if (password.trim().length() < 8) {
            log.warn("【管理员引导】BOOTSTRAP_ADMIN_PASSWORD 少于 8 位，已跳过");
            return;
        }

        Long adminCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, 9)
        );
        if (adminCount != null && adminCount > 0) {
            log.info("【管理员引导】系统已存在管理员，跳过初始化");
            return;
        }

        String normalizedPhone = phone.trim();
        SysUser existing = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, normalizedPhone), false
        );

        if (existing != null) {
            existing.setRole(9);
            existing.setStatus(1);
            existing.setPasswordHash(BCrypt.hashpw(password.trim(), BCrypt.gensalt()));
            existing.setUpdateTime(LocalDateTime.now());
            sysUserMapper.updateById(existing);
            log.info("【管理员引导】已将手机号 {} 对应账号提升为管理员", maskPhone(normalizedPhone));
            return;
        }

        SysUser admin = new SysUser();
        admin.setPhone(normalizedPhone);
        admin.setPasswordHash(BCrypt.hashpw(password.trim(), BCrypt.gensalt()));
        admin.setNickname(nickname == null || nickname.trim().isEmpty() ? "平台管理员" : nickname.trim());
        admin.setAvatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80");
        admin.setGender(0);
        admin.setRole(9);
        admin.setStatus(1);
        admin.setDeleted(0);
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(admin);

        UserWallet wallet = new UserWallet();
        wallet.setUserId(admin.getId());
        wallet.setBalance(0);
        wallet.setFrozenBalance(0);
        wallet.setTotalIncome(0);
        wallet.setVersion(0);
        wallet.setCreateTime(LocalDateTime.now());
        wallet.setUpdateTime(LocalDateTime.now());
        userWalletMapper.insert(wallet);

        log.info("【管理员引导】已创建首个管理员 userId={}, phone={}", admin.getId(), maskPhone(normalizedPhone));
    }

    private String maskPhone(String value) {
        if (value == null || value.length() < 7) return "***";
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
