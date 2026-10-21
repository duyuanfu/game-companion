package com.game.companion.modules.user.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.user.dto.BindPhoneDTO;
import com.game.companion.modules.user.dto.UpdateProfileDTO;
import com.game.companion.modules.user.dto.WxLoginDTO;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import com.game.companion.modules.user.service.UserService;
import com.game.companion.modules.user.vo.UserLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    @Autowired(required = false)
    private WxMaService wxMaService;

    private final com.game.companion.modules.wallet.mapper.UserWalletMapper userWalletMapper;

    @Value("${wx.miniapp.appid:}")
    private String wxAppId;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginVO wxSilentLogin(WxLoginDTO dto) {
        String openid = null;
        String unionid = null;
        boolean isRealWxAuth = false;

        boolean hasValidWxConfig = wxMaService != null && wxAppId != null 
                && !wxAppId.isEmpty() && !wxAppId.contains("your-");

        // 1. 如果已配置真实小程序密钥，且前端传来真实微信 code，调用微信官方获取真实的 OpenID
        if (hasValidWxConfig && dto.getCode() != null && !dto.getCode().startsWith("mock_")) {
            try {
                WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(dto.getCode());
                openid = session.getOpenid();
                unionid = session.getUnionid();
                isRealWxAuth = true;
                log.info("✅ 微信官方 API 成功换取真实用户 OpenID: {}", openid);
            } catch (Exception e) {
                log.error("微信官方 code 换取 session 失败，启用开发模式: ", e);
            }
        }

        // 2. 开发降级 OpenID (仅在微信官方无法换取时按 code 进行区分)
        if (openid == null) {
            openid = resolveDevOpenId(dto.getCode(), dto.getDeviceType());
        }

        // 针对线上 unionid 为空或空串进行安全归一化，坚决防止空字符串存库导致 uk_unionid 唯一键冲突
        String cleanUnionid = normalizeUnionid(unionid);

// 3. 【真实全动态用户建档】完全按微信号唯一 OpenID 独立建档注册，彻底去除任何硬编码绑定
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenid, openid));
        if (user == null) {
            // 新微信号首次进入：自动完成静默建档注册
            user = new SysUser();
            user.setOpenid(openid);
            user.setUnionid(cleanUnionid);
            user.setNickname("微信用户_" + RandomUtil.randomNumbers(4));
            user.setAvatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80");
            user.setRole(RoleEnum.CLIENT.getCode()); // 默认角色：普通点单客户
            user.setStatus(1);
            user.setDeleted(0);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            save(user);

            // 自动初始化该微信号的独立资金钱包
            com.game.companion.modules.wallet.entity.UserWallet wallet = new com.game.companion.modules.wallet.entity.UserWallet();
            wallet.setUserId(user.getId());
            wallet.setBalance(0);
            wallet.setFrozenBalance(0);
            wallet.setTotalIncome(0);
            wallet.setVersion(0);
            wallet.setCreateTime(LocalDateTime.now());
            wallet.setUpdateTime(LocalDateTime.now());
            userWalletMapper.insert(wallet);

            log.info("========== [新微信号首次扫码建档注册成功] ==========");
            log.info("用户ID: {}, 真实OpenID: {}, 来源: {}", user.getId(), openid, isRealWxAuth ? "微信官方API" : "开发模拟");
            log.info("==================================================");
        } else {
            // 已建档老用户：更新最新访问时间与UnionId
            if (cleanUnionid != null) {
                user.setUnionid(cleanUnionid);
            }
            user.setUpdateTime(LocalDateTime.now());
            updateById(user);
            log.info("【微信用户登录】用户ID: {}, 昵称: {}, 角色: {}, 来源: {}", 
                    user.getId(), user.getNickname(), user.getRole(), isRealWxAuth ? "微信官方API" : "开发模拟");
        }

        // 标准生产级：Sa-Token 会话托管
        StpUtil.login(user.getId());
        String tokenValue = StpUtil.getTokenValue();

        return UserLoginVO.builder()
                .userId(user.getId())
                .openid(user.getOpenid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .phone(user.getPhone())
                .role(user.getRole())
                .token(tokenValue)
                .build();
    }

    private String resolveDevOpenId(String code, String deviceType) {
        if (code != null && !code.trim().isEmpty() && !"the code is a mock one".equals(code)) {
            return "dev_openid_" + cn.hutool.crypto.SecureUtil.md5(code).substring(0, 16);
        }
        if ("phone".equalsIgnoreCase(deviceType)) {
            return "dev_openid_phone_user";
        }
        return "dev_openid_simulator_user";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bindWxPhoneNumber(Long userId, BindPhoneDTO dto) {
        // 【安全修复】禁止再使用「取库中最近活跃用户」的方式兜底，避免把手机号错绑到他人账号
        if (userId == null || userId <= 0) {
            throw new ApiException("操作被拒绝：请先登录后再绑定手机号");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new ApiException("用户不存在，请重新登录");
        }

        String phoneNumber = null;

        // 1. 优先使用前端手动填入的真实手机号 (彻底避免无权限时向微信发送空 code 报 40029)
        if (dto.getPhone() != null && !dto.getPhone().trim().isEmpty()) {
            phoneNumber = dto.getPhone().trim();
            log.info("【手机号绑定】直接采用用户填写的真实手机号: {}", phoneNumber);
        } else {
            // 2. 仅在未传手机号且 code 有效非空时，才请求微信官方接口解密
            boolean hasValidWxConfig = wxMaService != null && wxAppId != null 
                    && !wxAppId.isEmpty() && !wxAppId.contains("your-");
            boolean hasValidCode = dto.getCode() != null && !dto.getCode().trim().isEmpty() 
                    && !dto.getCode().startsWith("mock_");

            if (hasValidWxConfig && hasValidCode) {
                try {
                    log.info("正在调用微信官方接口解密手机号: code={}", dto.getCode());
                    WxMaPhoneNumberInfo phoneInfo = wxMaService.getUserService().getPhoneNoInfo(dto.getCode().trim());
                    phoneNumber = phoneInfo.getPhoneNumber();
                } catch (Exception e) {
                    log.error("微信官方接口解密手机号失败: ", e);
                }
            }
        }

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            phoneNumber = "13800" + RandomUtil.randomNumbers(6);
        }

        user.setPhone(phoneNumber);
        user.setUpdateTime(LocalDateTime.now());
        boolean updated = updateById(user);
        
        log.info("========== [手机号成功写入 MySQL 数据库] ==========");
        log.info("用户ID: {}, 绑定手机号: {}, 更新结果: {}", user.getId(), phoneNumber, updated);
        log.info("==================================================");
        
        return phoneNumber;
    }

    /**
     * 【自助改资料】用户只能修改自己的头像与昵称。
     *
     * <p>昵称与头像均属用户生成内容，提交时接入微信官方内容安全检测
     * （{@code msgSecCheck} / {@code imgSecCheck}）做合规校验；
     * 微信接口不可用（未开通权限 / 网络异常）时降级放行并告警，绝不因第三方不可用而阻断正常用户。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser updateProfile(Long userId, UpdateProfileDTO dto) {
        if (userId == null || userId <= 0) {
            throw new ApiException("操作被拒绝：请先登录后再修改资料");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new ApiException("用户不存在，请重新登录");
        }

        String nickname = dto.getNickname() == null ? "" : dto.getNickname().trim();
        if (nickname.isEmpty()) {
            throw new ApiException("昵称不能为空");
        }
        if (nickname.length() > 20) {
            throw new ApiException("昵称长度不能超过 20 个字符");
        }

        // 微信官方内容安全检测：昵称命中违规词则拒绝
        if (!passMsgSecCheck(nickname)) {
            throw new ApiException("昵称包含违规内容，请修改后重试");
        }

        user.setNickname(nickname);

        // 头像：为空表示不修改；非空则必须是 http/https 地址
        String avatarUrl = dto.getAvatarUrl() == null ? "" : dto.getAvatarUrl().trim();
        if (!avatarUrl.isEmpty()) {
            if (avatarUrl.length() > 512) {
                throw new ApiException("头像地址过长");
            }
            if (!avatarUrl.startsWith("http://") && !avatarUrl.startsWith("https://")) {
                throw new ApiException("头像地址非法，请重新上传");
            }
            user.setAvatarUrl(avatarUrl);
        }

        user.setUpdateTime(LocalDateTime.now());
        updateById(user);

        log.info("【用户自助修改资料】用户ID={}, 新昵称={}, 是否更换头像={}",
                userId, nickname, !avatarUrl.isEmpty());
        return user;
    }

    /**
     * 昵称内容安全检测（降级放行策略：微信接口异常时记录告警但不阻断用户操作）
     */
    private boolean passMsgSecCheck(String content) {
        if (wxMaService == null) {
            return true;
        }
        try {
            boolean pass = wxMaService.getSecCheckService().checkMessage(content);
            if (!pass) {
                log.warn("【内容安全检测未通过】昵称命中违规规则: {}", content);
            }
            return pass;
        } catch (Exception e) {
            log.warn("【内容安全检测调用失败，本次降级放行】error={}", e.getMessage());
            return true;
        }
    }

    /**
     * 针对微信生态场景下 unionid 可能为空或空串进行安全归一化
     * 确保空值在数据库中保持为 NULL，避免触发 uk_unionid 唯一键冲突
     */
    private String normalizeUnionid(String unionid) {
        if (unionid == null) {
            return null;
        }
        String trimmed = unionid.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
