## ADDED Requirements

### Requirement: 陪练入驻申请

系统 SHALL 提供 Web 端入驻申请表单，支持多游戏技能、段位截图与原声试听。

#### Scenario: 提交多游戏申请

- **WHEN** 用户填写真实姓名、手机号，并添加至少一个游戏技能（游戏名称、段位、单价、单位）
- **THEN** 前端 MUST 调用 `POST /api/companion/apply` 提交 `skills` 数组
- **AND** 段位截图 MUST 先上传并得到 URL 后写入 `rankImg`
- **AND** 未填写真实姓名、手机号或没有任何技能时 MUST 阻止提交并提示

#### Scenario: 原声为选填

- **WHEN** 用户未录制或未上传原声
- **THEN** 前端 MUST 允许提交申请
- **AND** MUST NOT 使用任何虚假音频地址兜底

#### Scenario: 上传凭证

- **WHEN** 用户选择段位截图或音频文件
- **THEN** 上传请求 MUST 携带当前 `satoken`
- **AND** 上传成功后 MUST 展示可预览的图片或可播放的音频

### Requirement: 入驻状态四态分流

系统 SHALL 根据 `GET /api/companion/my-status` 的返回，将陪练入口分为未申请、审核中、已通过、已驳回四种状态。

#### Scenario: 审核中

- **WHEN** `hasProfile=true` 且 `auditStatus=0`
- **THEN** 页面 MUST 展示「审核中」及已提交资料摘要
- **AND** MUST NOT 提供进入接单工作台的入口

#### Scenario: 已驳回

- **WHEN** `auditStatus=2`
- **THEN** 页面 MUST 展示驳回原因
- **AND** MUST 提供重新申请入口

#### Scenario: 审核通过

- **WHEN** `auditStatus=1` 或 `canAcceptOrder=true`
- **THEN** 页面 MUST 提供进入工作台的入口

### Requirement: 接单工作台

系统 SHALL 展示陪练工作台统计，并允许切换接单状态。

#### Scenario: 查看工作台

- **WHEN** 具备接单资格的陪练打开 `/companion/workbench`
- **THEN** 前端 MUST 调用 `GET /api/companion/workbench/stats` 展示余额、累计收益、本月完成单数、游戏、段位、定价与在线状态

#### Scenario: 切换在线状态

- **WHEN** 用户点击上线或休息
- **THEN** 前端 MUST 调用 `POST /api/companion/toggle-online?isOnline=...`
- **AND** 成功后 MUST 以最新状态更新界面
- **AND** 审核未通过时 MUST 展示后端拒绝原因

### Requirement: 接单与申请完结

系统 SHALL 允许具备资格的陪练在合法状态下接单与申请完结。

#### Scenario: 接单

- **WHEN** 陪练在状态 1 的订单点击接单
- **THEN** 前端 MUST 调用 `POST /api/order/accept/:orderSn`
- **AND** 成功后 MUST 刷新订单状态为服务中

#### Scenario: 申请完结

- **WHEN** 陪练在状态 2 的订单提交完结凭证或说明
- **THEN** 前端 MUST 调用 `POST /api/order/apply-finish/:orderSn`
- **AND** 成功后 MUST 展示订单进入申请完结状态

### Requirement: 收益与提现

系统 SHALL 展示陪练收益与提现记录，并支持提交提现申请。

#### Scenario: 查看收益

- **WHEN** 陪练打开收益区域
- **THEN** 前端 MUST 展示可用余额、累计收益、钱包流水与提现记录
- **AND** 金额 MUST 由分转换为元展示

#### Scenario: 申请提现

- **WHEN** 陪练输入不低于 1 元的提现金额并提交
- **THEN** 前端 MUST 以分调用 `POST /api/wallet/withdraw/apply`
- **AND** 金额低于 100 分或超过可用余额时 MUST 阻止提交并提示
- **AND** 成功后 MUST 刷新余额与提现记录
