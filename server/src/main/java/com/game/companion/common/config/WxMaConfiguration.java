package com.game.companion.common.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class WxMaConfiguration {

    @Value("${wx.miniapp.appid:}")
    private String appid;

    @Value("${wx.miniapp.secret:}")
    private String secret;

    @Bean
    @ConditionalOnMissingBean
    public WxMaService wxMaService() {
        if (appid == null || appid.trim().isEmpty() || appid.contains("your-")) {
            log.warn("⚠️ [WxMaService] 未配置真实的微信小程序 AppID，微信官方 API 处于模拟降级状态");
            return null;
        }

        try {
            WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
            config.setAppid(appid.trim());
            config.setSecret(secret != null ? secret.trim() : "");

            WxMaService service = new WxMaServiceImpl();
            service.setWxMaConfig(config);
            log.info("✅ [WxMaService] 微信官方小程序服务引擎已成功加载！AppID: {}", appid);
            return service;
        } catch (Exception e) {
            log.error("❌ [WxMaService] 初始化微信小程序服务失败: ", e);
            return null;
        }
    }
}
