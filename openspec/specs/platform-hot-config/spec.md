# platform-hot-config Specification

## Purpose

定义平台运营参数的「热配置」能力：管理员在手机端修改抽佣比例、支付模式、微信支付商户参数后，
必须真正影响线上业务逻辑并秒级生效，而不仅仅是把数值写进数据库。

## Requirements

### Requirement: 抽佣比例热配置真实生效

系统 SHALL 在每次计算平台抽佣时读取 `sys_config` 中的 `platform.commission-percent`，
MUST NOT 使用启动期注入的静态值。

#### Scenario: 管理员将抽佣比例修改为 20%

- **WHEN** 管理员保存 `commissionPercent = 20` 后随即完成一笔订单履约
- **THEN** 生成的 `order_settlement.platform_gross_fee` MUST 等于 `round(total_amount * 0.20)`
- **AND** `companion_income` MUST 等于 `total_amount - platform_gross_fee`
- **AND** 该行为 MUST NOT 要求重启后端服务

#### Scenario: 热配置缺失时兜底

- **WHEN** `sys_config` 中不存在 `platform.commission-percent` 记录
- **THEN** 系统回退到 `platform.commission-rate` 配置项（默认 0.10）计算

### Requirement: 支付模式热配置真实生效

系统 SHALL 在每次获取支付网关时读取 `sys_config` 中的 `platform.payment-mode`，
在 `mock` 与 `wechat` 之间切换时业务代码零改动。

#### Scenario: 管理员切换为真实微信支付

- **WHEN** `platform.payment-mode` 被修改为 `wechat`
- **THEN** 后续下单 MUST 走微信支付 V3 统一下单，返回前端 `wx.requestPayment` 所需的五要素

### Requirement: 支付商户参数热加载

系统 SHALL 在商户参数（AppID / MchId / APIv3Key / 商户私钥）发生变化时自动重建微信支付服务实例，
MUST NOT 要求重启服务。

#### Scenario: 管理员补全商户私钥

- **WHEN** 管理员在热配置中心填入商户私钥 PEM
- **THEN** 系统 MUST 在下一次支付请求时自动加载新私钥并完成签名

### Requirement: 敏感配置不得硬编码入库

系统 MUST 通过环境变量或本地私有配置文件注入微信 AppID / Secret / 商户密钥，
版本库中的配置文件 MUST NOT 包含真实密钥。

#### Scenario: 生产环境启动

- **WHEN** 未注入 `WX_MINIAPP_SECRET` 等环境变量时启动服务
- **THEN** 微信生态相关能力 MUST 以「未配置」状态明确告警，MUST NOT 静默使用历史硬编码密钥
