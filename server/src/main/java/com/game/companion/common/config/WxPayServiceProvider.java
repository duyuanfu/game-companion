package com.game.companion.common.config;

import com.game.companion.modules.admin.service.PlatformConfigService;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 【微信支付商户服务动态提供者】
 *
 * <p>管理员在手机端热配置中心修改 AppID / MchId / APIv3Key / 商户私钥后，
 * 本组件会自动感知配置变化并重建 {@link WxPayService} 实例，真正做到「秒级热生效」，
 * 而不仅仅是把参数写进数据库却无人读取。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WxPayServiceProvider {

    private final PlatformConfigService platformConfigService;

    @Value("${wx.pay.appId:}")
    private String defaultAppId;

    @Value("${wx.pay.mchId:}")
    private String defaultMchId;

    @Value("${wx.pay.apiV3Key:}")
    private String defaultApiV3Key;

    @Value("${wx.pay.keyPath:}")
    private String defaultKeyPath;

    @Value("${wx.pay.certPath:}")
    private String defaultCertPath;

    @Value("${wx.pay.notify-url:}")
    private String defaultNotifyUrl;

    private volatile WxPayService cachedService;
    private volatile String cachedSignature;

    /**
     * 获取当前生效的微信支付服务实例；商户参数不完整时返回 null（由调用方给出明确报错）。
     */
    public WxPayService getWxPayService() {
        String appId = resolve("wx.pay.appid", defaultAppId);
        String mchId = resolve("wx.pay.mchid", defaultMchId);
        String apiV3Key = resolve("wx.pay.apiv3key", defaultApiV3Key);
        String privateKeyContent = resolve("wx.pay.key-content", "");

        if (isBlank(appId) || isBlank(mchId) || isBlank(apiV3Key)
                || appId.contains("your-") || mchId.contains("your-") || apiV3Key.contains("your-")) {
            return null;
        }

        // 配置指纹：任一关键参数变化即重建实例
        String signature = appId + "|" + mchId + "|" + apiV3Key + "|" + privateKeyContent.hashCode();
        WxPayService current = cachedService;
        if (current != null && signature.equals(cachedSignature)) {
            return current;
        }

        synchronized (this) {
            if (cachedService != null && signature.equals(cachedSignature)) {
                return cachedService;
            }
            try {
                WxPayConfig payConfig = new WxPayConfig();
                payConfig.setAppId(appId);
                payConfig.setMchId(mchId);
                payConfig.setApiV3Key(apiV3Key);
                if (!isBlank(defaultNotifyUrl)) {
                    payConfig.setNotifyUrl(defaultNotifyUrl.trim());
                }

                // 商户私钥：优先使用热配置中的 PEM 文本，其次使用本地证书路径
                if (!isBlank(privateKeyContent)) {
                    payConfig.setPrivateKeyContent(privateKeyContent.getBytes(StandardCharsets.UTF_8));
                } else if (!isBlank(defaultKeyPath)) {
                    payConfig.setKeyPath(defaultKeyPath);
                    payConfig.setPrivateKeyPath(defaultKeyPath);
                }
                if (!isBlank(defaultCertPath)) {
                    payConfig.setPrivateCertPath(defaultCertPath);
                }

                WxPayService service = new WxPayServiceImpl();
                service.setConfig(payConfig);

                cachedService = service;
                cachedSignature = signature;
                log.info("✅ [WxPayService] 微信支付商户服务已加载 (mchId={}, 私钥来源={})",
                        mchId, isBlank(privateKeyContent) ? "本地证书路径" : "热配置PEM");
                return service;
            } catch (Exception e) {
                log.error("❌ [WxPayService] 初始化微信支付商户服务失败: ", e);
                cachedService = null;
                cachedSignature = null;
                return null;
            }
        }
    }

    private String resolve(String key, String defaultValue) {
        try {
            String value = platformConfigService.getConfig(key, defaultValue);
            return value == null ? "" : value.trim();
        } catch (Exception e) {
            return defaultValue == null ? "" : defaultValue.trim();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
