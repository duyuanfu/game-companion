# order-automation Specification

## Purpose

定义订单全生命周期的自动化能力：接单超时自动退款、完结超时自动结单，
以及支付事实的幂等落盘，确保无人值守场景下不会产生漏单、错结算或重复入账。

## Requirements

### Requirement: 接单超时自动退款

系统 SHALL 通过后台调度任务周期性扫描处于「已付款待接单(1)」且下单时间超过
`platform.order-accept-timeout-minutes`（默认 10 分钟）的订单，自动触发原路全额退款。

#### Scenario: 陪玩师超时未接单

- **WHEN** 订单处于状态 1 且 `create_time` 早于当前时间减去超时阈值
- **THEN** 系统自动执行退款，订单状态 MUST 流转为「已退款(6)」
- **AND** `pay_flow` MUST 被标记为已全额退款(3) 并冲正通道费 `refund_channel_fee`
- **AND** MUST NOT 产生任何人工干预

#### Scenario: 未到超时时间的订单

- **WHEN** 订单处于状态 1 但尚未超过超时阈值
- **THEN** 系统 MUST NOT 触发退款
- **AND** 手动调用超时检测接口同样 MUST 返回「无需超时退款」

### Requirement: 完结超时自动结单

系统 SHALL 扫描处于「陪玩师申请完结(3)」且超过 `platform.order-finish-timeout-hours`（默认 24 小时）
未确认的订单，自动完成清算与钱包入账。

#### Scenario: 客户长期未确认打款

- **WHEN** 订单处于状态 3 且 `update_time` 早于当前时间减去超时阈值
- **THEN** 系统自动生成 `order_settlement` 清算单并把收益划入陪玩师钱包

### Requirement: 支付事实幂等落盘

系统 SHALL 在支付成功落盘 `pay_flow` 之前校验订单状态，同一订单 MUST 只产生一条支付事实。

#### Scenario: 微信重复推送支付回调

- **WHEN** 同一订单的支付成功回调被重复投递
- **THEN** 系统 MUST 忽略重复回调，MUST NOT 重复插入 `pay_flow`、MUST NOT 重复触发派单通知

#### Scenario: Mock 支付与真实支付回调混合

- **WHEN** 订单已处于状态 1（待接单）后再次收到支付成功回调
- **THEN** 系统 MUST 记录幂等告警并直接返回，MUST NOT 重复落盘

### Requirement: 订单状态机强校验

系统 SHALL 在每次订单状态流转前校验前置状态，MUST NOT 允许跳步操作。

#### Scenario: 未接单直接申请结单

- **WHEN** 订单处于状态 1 时陪玩师调用申请结单
- **THEN** 系统 MUST 拒绝并返回业务异常

#### Scenario: 未申请完结直接确认打款

- **WHEN** 订单处于状态 1 或 2 时客户调用确认打款
- **THEN** 系统 MUST 拒绝并返回业务异常

#### Scenario: 重复确认打款

- **WHEN** 订单已完成(4)后再次调用确认打款
- **THEN** 系统 MUST 拒绝并返回业务异常
- **AND** MUST NOT 产生第二张清算单，MUST NOT 重复给钱包加钱

### Requirement: 微信支付 V3 真实可用

系统 SHALL 提供基于微信支付 V3 的 JSAPI 下单、原路退款与商家转账到零钱能力，
并在商户参数缺失或调用失败时明确报错，MUST NOT 静默返回「成功」。

#### Scenario: 商户参数未配置

- **WHEN** 支付模式为 `wechat` 但商户号或 APIv3Key 缺失
- **THEN** 系统 MUST 抛出明确的配置缺失异常，MUST NOT 伪造支付成功

#### Scenario: 退款被微信拒绝

- **WHEN** 微信退款接口返回非 `SUCCESS` / `PROCESSING` 状态
- **THEN** 系统 MUST 抛出异常，MUST NOT 将订单标记为已退款

#### Scenario: 微信支付成功异步回调

- **WHEN** 微信向 `/pay/notify` 推送加密的支付结果通知
- **THEN** 系统 MUST 完成官方 RSA 验签与 AES-GCM 解密后方可处理
- **AND** 验签失败 MUST 直接拒绝，MUST NOT 落盘任何支付事实
- **AND** 验签通过且交易状态为 SUCCESS 时 MUST 落盘 `pay_flow` 并把订单流转为「待接单(1)」
