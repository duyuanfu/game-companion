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
     * 微信 getPhoneNumber 原生组件一键绑定手机号 (防漏单)
     */
    String bindWxPhoneNumber(Long userId, BindPhoneDTO dto);
}
