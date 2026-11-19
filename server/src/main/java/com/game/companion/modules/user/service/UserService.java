package com.game.companion.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.game.companion.modules.user.dto.BindPhoneDTO;
import com.game.companion.modules.user.dto.WxLoginDTO;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.vo.UserLoginVO;

public interface UserService extends IService<SysUser> {

    /**
     * 微信小程序一键静默登录 (无感知)
     */
    UserLoginVO wxSilentLogin(WxLoginDTO dto);

    /**
     * Web 手机号 + 密码登录。
     */
    UserLoginVO passwordLogin(com.game.companion.modules.user.dto.PasswordLoginDTO dto);

    /**
     * Web 手机号注册，注册成功后直接登录。
     */
    UserLoginVO register(com.game.companion.modules.user.dto.RegisterDTO dto);

    /**
     * 修改当前登录用户密码。
     */
    void changePassword(Long userId, com.game.companion.modules.user.dto.ChangePasswordDTO dto);

    /**
     * 微信 getPhoneNumber 原生组件一键绑定手机号 (防漏单)
     */
    String bindWxPhoneNumber(Long userId, BindPhoneDTO dto);

    /**
     * 用户自助修改头像与昵称 (仅限本人)
     *
     * @return 更新后的用户档案
     */
    SysUser updateProfile(Long userId, com.game.companion.modules.user.dto.UpdateProfileDTO dto);
}
