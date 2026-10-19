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
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import com.game.companion.modules.companion.mapper.CompanionSkillMapper;
import com.game.companion.modules.user.dto.BindPhoneDTO;
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

    private final CompanionProfileMapper companionProfileMapper;
    private final CompanionSkillMapper companionSkillMapper;

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

        // 2. 开发降级 OpenID
        if (openid == null) {
            openid = resolveDevOpenId(dto.getDeviceType());
        }

        // 3. 🌟 核心破案点：双端角色与真实微信身份精准绑定
        boolean isPhoneDevice = "phone".equalsIgnoreCase(dto.getDeviceType());
        SysUser user;

        if (isPhoneDevice) {
            // 【手机真机微信端】-> 对应陪练师【甜心小鹿 (ID: 20001, role: 2)】
            user = getById(20001L);
            if (user == null) {
                user = new SysUser();
                user.setId(20001L);
                user.setNickname("甜心小鹿");
                user.setAvatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80");
                user.setPhone("13800000001");
                user.setRole(RoleEnum.COMPANION.getCode());
                user.setStatus(1);
                user.setDeleted(0);
                user.setCreateTime(LocalDateTime.now());
            }

            // 🌟 最关键一步：把当前扫码真机手机换出来的【真实微信 OpenID】直接绑定给甜心小鹿！
            // 只有这样，后续向甜心小鹿推送微信卡片时，才能精准推到你手机微信里！
            user.setOpenid(openid);
            if (unionid != null) user.setUnionid(unionid);
            user.setRole(RoleEnum.COMPANION.getCode());
            user.setUpdateTime(LocalDateTime.now());
            saveOrUpdate(user);

            ensureCompanionProfileExists(user.getId());
            log.info("========== [真机设备已绑定陪玩师 甜心小鹿] ==========");
            log.info("用户ID: 20001, 绑定的真实微信OpenID: {}, 来源: {}", openid, isRealWxAuth ? "微信官方API" : "开发模拟");
            log.info("==================================================");
        } else {
            // 【电脑模拟器端】-> 对应【点单老板客户 (role: 1)】
            user = getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenid, openid));
            if (user == null) {
                user = new SysUser();
                user.setOpenid(openid);
                user.setUnionid(unionid);
                user.setNickname("点单老板_" + RandomUtil.randomNumbers(4));
                user.setAvatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80");
                user.setPhone(null);
                user.setRole(RoleEnum.CLIENT.getCode());
                user.setStatus(1);
                user.setDeleted(0);
                user.setCreateTime(LocalDateTime.now());
                user.setUpdateTime(LocalDateTime.now());
                save(user);
                log.info("========== [模拟器电脑端注册点单老板] ==========");
                log.info("用户ID: {}, OpenID: {}", user.getId(), openid);
                log.info("==============================================");
            } else {
                user.setUpdateTime(LocalDateTime.now());
                updateById(user);
            }
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

    private void ensureCompanionProfileExists(Long userId) {
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, userId)
        );
        if (profile == null) {
            profile = new CompanionProfile();
            profile.setUserId(userId);
            profile.setRealName("鹿小甜");
            profile.setBio("主玩软辅中单，听指挥超温柔，输赢都不压力~");
            profile.setVoiceUrl("/static/audio/voice_sweet_girl.mp3");
            profile.setVoiceDuration(6);
            profile.setIsOnline(1);
            profile.setRating(new java.math.BigDecimal("5.0"));
            profile.setOrderCount(128);
            profile.setAuditStatus(1);
            profile.setCreateTime(LocalDateTime.now());
            companionProfileMapper.insert(profile);

            CompanionSkill skill = new CompanionSkill();
            skill.setCompanionId(profile.getId());
            skill.setUserId(userId);
            skill.setGameId(1L);
            skill.setGameName("王者荣耀");
            skill.setRankName("最强王者 35星");
            skill.setPrice(2500);
            skill.setUnit("小时");
            skill.setStatus(1);
            skill.setCreateTime(LocalDateTime.now());
            companionSkillMapper.insert(skill);
        }
    }

    private String resolveDevOpenId(String deviceType) {
        if ("phone".equalsIgnoreCase(deviceType)) {
            return "mock_openid_companion_1";
        }
        return "mock_openid_client_simulator";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bindWxPhoneNumber(Long userId, BindPhoneDTO dto) {
        SysUser user = getById(userId);
        if (user == null) {
            user = getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenid, "mock_dev_developer_openid"));
        }
        if (user == null) {
            throw new ApiException("未找到对应用户记录");
        }

        String phoneNumber = null;
        boolean hasValidWxConfig = wxMaService != null && wxAppId != null 
                && !wxAppId.isEmpty() && !wxAppId.contains("your-");

        if (hasValidWxConfig) {
            try {
                WxMaPhoneNumberInfo phoneInfo = wxMaService.getUserService().getPhoneNoInfo(dto.getCode());
                phoneNumber = phoneInfo.getPhoneNumber();
            } catch (Exception e) {
                log.error("微信官方接口解密手机号失败: ", e);
            }
        }

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
                phoneNumber = dto.getPhone();
            } else {
                phoneNumber = "13800" + RandomUtil.randomNumbers(6);
            }
        }

        user.setPhone(phoneNumber);
        user.setUpdateTime(LocalDateTime.now());
        boolean updated = updateById(user);
        
        log.info("========== [手机号成功写入 MySQL 数据库] ==========");
        log.info("用户ID: {}, 绑定手机号: {}, 更新结果: {}", user.getId(), phoneNumber, updated);
        log.info("==================================================");
        
        return phoneNumber;
    }
}
