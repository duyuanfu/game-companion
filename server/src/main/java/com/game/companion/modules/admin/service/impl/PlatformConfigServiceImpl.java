package com.game.companion.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.modules.admin.dto.PlatformConfigDTO;
import com.game.companion.modules.admin.entity.SysConfig;
import com.game.companion.modules.admin.mapper.SysConfigMapper;
import com.game.companion.modules.admin.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformConfigServiceImpl implements PlatformConfigService {

    private final SysConfigMapper sysConfigMapper;

    @Value("${platform.payment-mode:mock}")
    private String defaultPaymentMode;

    @Value("${platform.commission-rate:0.10}")
    private Double defaultCommissionRate;

    @Value("${platform.channel-fee-rate:0.006}")
    private Double defaultChannelFeeRate;

    @Value("${wx.pay.appId:}")
    private String defaultAppId;

    @Value("${wx.miniapp.secret:}")
    private String defaultAppSecret;

    @Value("${wx.pay.mchId:}")
    private String defaultMchId;

    @Value("${wx.pay.apiV3Key:}")
    private String defaultApiV3Key;

    @Value("${wx.miniapp.template-id.new-order:}")
    private String defaultTemplateId;

    @Override
    public PlatformConfigDTO getPlatformConfig() {
        PlatformConfigDTO dto = new PlatformConfigDTO();
        dto.setPaymentMode(getConfig("platform.payment-mode", defaultPaymentMode));
        
        String commissionStr = getConfig("platform.commission-percent", String.valueOf(defaultCommissionRate * 100));
        try {
            dto.setCommissionPercent(Double.parseDouble(commissionStr));
        } catch (Exception e) {
            dto.setCommissionPercent(10.0);
        }

        // 微信通道费率 (合同费率预估值, 可按商户实际合同调整)
        String channelFeeStr = getConfig("platform.channel-fee-rate", String.valueOf(defaultChannelFeeRate * 100));
        try {
            dto.setChannelFeePercent(Double.parseDouble(channelFeeStr));
        } catch (Exception e) {
            dto.setChannelFeePercent(0.6);
        }

        dto.setAppId(getConfig("wx.pay.appid", defaultAppId));
        dto.setAppSecret(getConfig("wx.miniapp.secret", defaultAppSecret));
        dto.setMchId(getConfig("wx.pay.mchid", defaultMchId));
        dto.setApiV3Key(getConfig("wx.pay.apiv3key", defaultApiV3Key));
        dto.setApiClientKeyContent(getConfig("wx.pay.key-content", ""));
        dto.setNewOrderTemplateId(getConfig("wx.miniapp.template-id.new-order", defaultTemplateId));

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePlatformConfig(PlatformConfigDTO dto) {
        if (dto.getPaymentMode() != null) {
            saveOrUpdateConfig("platform.payment-mode", dto.getPaymentMode().trim(), "支付运行模式: mock/wechat");
        }
        if (dto.getCommissionPercent() != null) {
            saveOrUpdateConfig("platform.commission-percent", String.valueOf(dto.getCommissionPercent()), "平台抽佣比例(%)");
        }
        if (dto.getChannelFeePercent() != null) {
            saveOrUpdateConfig("platform.channel-fee-rate", String.valueOf(dto.getChannelFeePercent()),
                    "微信支付通道费率(%)，合同费率预估值，最终以微信交易账单对账为准");
        }
        if (dto.getAppId() != null) {
            saveOrUpdateConfig("wx.pay.appid", dto.getAppId().trim(), "Web支付AppID(服务号/网站应用)");
        }
        if (dto.getAppSecret() != null) {
            saveOrUpdateConfig("wx.miniapp.secret", dto.getAppSecret().trim(), "小程序AppSecret");
        }
        if (dto.getMchId() != null) {
            saveOrUpdateConfig("wx.pay.mchid", dto.getMchId().trim(), "微信支付商户号");
        }
        if (dto.getApiV3Key() != null) {
            saveOrUpdateConfig("wx.pay.apiv3key", dto.getApiV3Key().trim(), "微信支付APIv3密钥");
        }
        if (dto.getApiClientKeyContent() != null) {
            saveOrUpdateConfig("wx.pay.key-content", dto.getApiClientKeyContent().trim(), "微信支付商户私钥PEM文本内容");
        }
        if (dto.getNewOrderTemplateId() != null) {
            saveOrUpdateConfig("wx.miniapp.template-id.new-order", dto.getNewOrderTemplateId().trim(), "新订单订阅消息模板ID");
        }

        log.info("【系统全局热配置更新成功】最新支付模式: {}, 抽佣比例: {}%, AppID: {}",
                dto.getPaymentMode(), dto.getCommissionPercent(), dto.getAppId());
    }

    @Override
    public String getConfig(String key, String defaultValue) {
        try {
            SysConfig config = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key)
            );
            if (config != null && config.getConfigValue() != null && !config.getConfigValue().trim().isEmpty()) {
                return config.getConfigValue().trim();
            }
        } catch (Exception e) {
            log.warn("读取动态配置失败, 走默认值: key={}, error={}", key, e.getMessage());
        }
        return defaultValue;
    }

    private void saveOrUpdateConfig(String key, String value, String remark) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key)
        );
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setRemark(remark);
            config.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(value);
            config.setRemark(remark);
            config.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.updateById(config);
        }
    }
}
