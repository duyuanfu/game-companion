## ADDED Requirements

### Requirement: 浏览与筛选陪练

系统 SHALL 在 Web 端展示陪练列表，支持按游戏筛选，并屏蔽当前陪练本人。

#### Scenario: 查看精选陪练

- **WHEN** 用户打开 `/companions`
- **THEN** 前端 MUST 调用 `GET /api/companion/list` 展示头像、昵称、游戏、段位、单价、评分、接单量、在线状态
- **AND** 列表 MUST 支持按游戏名称筛选
- **AND** 当前登录用户为陪练时，其本人卡片 MUST 被隐藏

#### Scenario: 列表为空

- **WHEN** 接口返回空列表
- **THEN** 页面 MUST 展示空状态与清除筛选的操作入口

### Requirement: 陪练详情与技能试听

系统 SHALL 提供陪练详情页，展示技能定价、真实评价与原声试听。

#### Scenario: 查看技能与评价

- **WHEN** 用户打开 `/companions/:userId`
- **THEN** 前端 MUST 调用 `GET /api/companion/skills` 展示该陪练全部上架技能的价格、段位与单位
- **AND** MUST 调用 `GET /api/order/review/companion/:companionUserId` 分页展示真实评价
- **AND** 存在 `voiceUrl` 时 MUST 提供可播放的原声控件，未录制时 MUST 展示「暂未录制原声」

#### Scenario: 未登录用户尝试下单

- **WHEN** 匿名用户点击「去下单」或「先聊两句」
- **THEN** 前端 MUST 引导到登录页
- **AND** 登录成功后 MUST 返回原陪练页面

### Requirement: 创建订单

系统 SHALL 支持从陪练技能进入确认订单页，实时联动单价、数量与总额，并提交现有下单接口。

#### Scenario: 填写并提交订单

- **WHEN** 用户选择技能、输入数量、游戏区服与游戏账号
- **THEN** 页面 MUST 实时展示单价 × 数量得出的应付总额
- **AND** 提交时 MUST 调用 `POST /api/order/create`，参数包含 `companionUserId`、`skillId`、`quantity`、`gameServer`、`gameAccount` 与可选 `clientRemark`
- **AND** 缺少必填项时 MUST 在字段级提示且不发起请求

#### Scenario: 预约自己

- **WHEN** 当前用户与目标陪练为同一账号
- **THEN** 前端 MUST 阻止进入下单或提交，并给出提示
- **AND** 后端拒绝时 MUST 原样展示后端错误信息

### Requirement: 支付与降级提示

系统 SHALL 根据下单返回的支付结果完成 mock 支付或给出微信支付的明确降级提示。

#### Scenario: Mock 模式支付

- **WHEN** 下单或支付接口返回 `payType=mock` 或 `isMockPaid=true`
- **THEN** 前端 MUST 提示支付成功并跳转订单详情
- **AND** 订单详情 MUST 展示最新订单状态

#### Scenario: 微信支付模式

- **WHEN** 接口返回微信 JSAPI 五要素而非已支付结果
- **THEN** 前端 MUST 提示当前为微信支付模式，需在微信小程序内完成支付
- **AND** MUST NOT 展示支付成功或修改本地订单状态

### Requirement: 订单列表与筛选

系统 SHALL 按客户或陪练视角展示当前用户订单，支持状态筛选、分页与待接单倒计时。

#### Scenario: 客户查看订单

- **WHEN** 用户打开 `/orders` 且视角为客户
- **THEN** 前端 MUST 以 `roleType=client` 调用 `GET /api/order/my-list`
- **AND** MUST 支持全部、待接单、服务中、待确认、已完成、已结束筛选
- **AND** MUST 展示分页信息

#### Scenario: 待接单倒计时

- **WHEN** 订单状态为 1 且未超过接单超时阈值
- **THEN** 列表或详情 MUST 展示剩余时间倒计时
- **AND** 倒计时归零后 MUST 通过重新拉取确认状态，不得在前端直接把订单改为已退款

#### Scenario: 切换陪练视角

- **WHEN** 陪练或管理员切换到陪练视角
- **THEN** 前端 MUST 以 `roleType=companion` 查询订单
- **AND** 列表 MUST 仅展示与当前账号相关的订单

### Requirement: 订单详情与履约操作

系统 SHALL 在订单详情展示完整时间轴、金额与支付事实，并按当前角色与状态提供合法操作。

#### Scenario: 客户撤回待接单订单

- **WHEN** 客户在状态 1 的订单详情点击撤回并确认
- **THEN** 前端 MUST 调用 `POST /api/order/cancel/:orderSn`
- **AND** 成功后 MUST 刷新详情与列表，并展示退款结果

#### Scenario: 客户确认完结

- **WHEN** 订单状态为 3 且当前用户为下单客户
- **THEN** 页面 MUST 提供确认完结操作并调用 `POST /api/order/confirm/:orderSn`
- **AND** 成功后 MUST 更新状态并刷新钱包相关数据

#### Scenario: 发起售后维权

- **WHEN** 客户在服务中或待确认订单填写维权原因并提交
- **THEN** 前端 MUST 调用 `POST /api/order/dispute/apply/:orderSn`
- **AND** 成功后 MUST 展示订单进入维权争议中，并隐藏重复发起入口

#### Scenario: 展示金额与支付流水

- **WHEN** 页面展示订单详情
- **THEN** MUST 展示标价、优惠、实付、支付流水号与通道费（若后端返回）
- **AND** 客户视角 MUST NOT 展示平台内部清算收益明细

### Requirement: 订单内聊天

系统 SHALL 提供按订单隔离的聊天室，支持轮询刷新、发送与失败重试。

#### Scenario: 收发消息

- **WHEN** 用户进入 `/chat/:orderSn`
- **THEN** 前端 MUST 调用 `GET /api/chat/messages?orderSn=...` 展示历史消息
- **AND** 页面可见期间 MUST 每 3 秒轮询新消息
- **AND** 发送时 MUST 调用 `POST /api/chat/send`，成功后更新消息列表

#### Scenario: 发送失败

- **WHEN** 消息发送请求失败
- **THEN** 前端 MUST 保留输入内容并标记该条消息发送失败
- **AND** MUST 提供重试操作

#### Scenario: 离开页面

- **WHEN** 用户离开聊天页或页面不可见
- **THEN** 前端 MUST 停止轮询

### Requirement: 订单评价

系统 SHALL 允许已完成订单的客户提交真实评分与评价。

#### Scenario: 提交评价

- **WHEN** 客户在状态 4 的订单中提交 1 至 5 星评分与可选标签、留言
- **THEN** 前端 MUST 调用 `POST /api/order/review/submit`
- **AND** 成功后 MUST 展示已提交的评价内容
- **AND** MUST 阻止对未完成或非本人订单提交评价

### Requirement: 个人资料与手机号

系统 SHALL 允许用户查看和修改本人资料，并上传头像。

#### Scenario: 修改昵称与头像

- **WHEN** 用户修改昵称或上传头像并保存
- **THEN** 前端 MUST 先通过 `POST /api/file/upload` 上传图片（携带 `satoken`），再调用 `PUT /api/user/profile`
- **AND** 成功后 MUST 使用后端返回的最新资料刷新界面

#### Scenario: 绑定手机号

- **WHEN** 用户输入 11 位手机号并确认绑定
- **THEN** 前端 MUST 校验格式后调用 `POST /api/user/bind-phone`
- **AND** 成功后 MUST 展示脱敏后的手机号
