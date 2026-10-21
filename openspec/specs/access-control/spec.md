# access-control Specification

## Purpose

定义「开黑搭子」平台的接口鉴权与越权防护能力：所有对外接口的身份一律以登录会话为准，
杜绝通过请求参数伪造身份所导致的越权接单、越权结单、越权改配置等资金安全风险。

## Requirements

### Requirement: 默认拒绝的登录态拦截

系统 SHALL 对所有 `/api/**` 请求强制校验登录态，仅显式白名单接口可匿名访问。
白名单 MUST 限定为：微信静默登录、游戏品类字典、陪练师公开浏览与技能定价、多媒体直读播放、微信支付异步回调、接口文档与错误页。

#### Scenario: 未携带凭证访问业务接口

- **WHEN** 客户端在未携带有效 `satoken` 的情况下请求任意非白名单接口（如 `/order/my-list`、`/admin/config/save`）
- **THEN** 系统返回 HTTP 200 且响应体业务码为 401，消息提示登录已失效
- **AND** 系统 MUST NOT 执行任何业务逻辑或数据读写

#### Scenario: 携带有效凭证访问业务接口

- **WHEN** 客户端携带有效 `satoken` 请求业务接口
- **THEN** 系统正常放行并基于该会话中的用户身份执行业务逻辑

### Requirement: 身份不可由客户端参数覆盖

系统 SHALL 忽略或拒绝一切来自请求参数的身份标识（`userId` / `clientUserId` / `companionUserId` / `adminUserId` / `senderId`），
业务身份 MUST 统一从当前登录会话解析。

#### Scenario: 伪造下单人参数

- **WHEN** 攻击者在 `/order/create` 请求体中额外携带 `clientUserId` 指向他人
- **THEN** 系统忽略该参数，订单 MUST 归属于当前登录用户本人

#### Scenario: 伪造接单人参数

- **WHEN** 攻击者调用 `/order/accept/{orderSn}?companionUserId=他人ID`
- **THEN** 系统忽略该参数，接单主体 MUST 为当前登录用户

### Requirement: 角色级权限校验

系统 SHALL 对平台特权接口强制校验 `sys_user.role`，非授权角色 MUST 返回 403。

#### Scenario: 普通用户访问管理员接口

- **WHEN** 角色为 1（客户）或 2（陪玩师）的用户请求 `/admin/**`、`/wallet/admin/**`、`/order/dispute/audit/**`
- **THEN** 系统返回业务码 403 并拒绝执行

#### Scenario: 管理员访问管理员接口

- **WHEN** 角色为 9（管理员）的用户请求管理员接口
- **THEN** 系统正常放行

### Requirement: 资源级越权防护

系统 SHALL 在读取或变更订单、聊天室、评价等资源时校验当前用户与该资源的关系。

#### Scenario: 查阅他人订单详情

- **WHEN** 当前用户既不是该订单的下单客户、接单陪玩师，也不是管理员
- **THEN** 系统返回业务码 403，不返回订单详情（避免泄露游戏账号与区服等隐私）

#### Scenario: 访问非成员聊天室

- **WHEN** 当前用户不是订单房间的成员（客户/陪玩师），也不是咨询房间的双方之一
- **THEN** 系统返回业务码 403，聊天记录与发言接口均被拒绝

### Requirement: 开发期特权工具生产禁用

系统 SHALL 通过 `platform.dev-tools-enabled` 开关控制诊断接口与身份切换接口。
生产环境该开关 MUST 为 `false`。

#### Scenario: 生产环境调用身份切换接口

- **WHEN** `platform.dev-tools-enabled=false` 时调用 `/user/mock-switch-role`
- **THEN** 系统返回业务码 403
- **AND** 即使在开启状态，也 MUST 仅允许切换调用者本人的角色，MUST NOT 允许切换他人角色
