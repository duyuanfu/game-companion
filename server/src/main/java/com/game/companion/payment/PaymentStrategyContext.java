package com.game.companion.payment;

import com.game.companion.modules.admin.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentStrategyContext {

    @Value("${platform.payment-mode:mock}")
    private String paymentMode;

    private final Map<String, PaymentService> paymentServiceMap;

    @Autowired
    @Lazy
    private PlatformConfigService platformConfigService;

    /**
     * 获取当前生效的支付服务实例 (优先从数据库读取热配置)
     */
    public PaymentService getService() {
        String activeMode = paymentMode;
        if (platformConfigService != null) {
            activeMode = platformConfigService.getConfig("platform.payment-mode", paymentMode);
        }
        if ("wechat".equalsIgnoreCase(activeMode)) {
            return paymentServiceMap.get("wechatPaymentService");
        }
        return paymentServiceMap.get("mockPaymentService");
    }
}
