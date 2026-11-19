## ADDED Requirements

### Requirement: 经营大盘

系统 SHALL 在管理端首页展示后端已有经营汇总指标。

#### Scenario: 查看今日经营数据

- **WHEN** 管理员打开 `/admin`
- **THEN** 前端 MUST 调用 `GET /api/admin/dashboard/summary` 展示今日订单数、交易流水、平台毛抽佣、微信通道费、平台净利润、在线陪练数与待审数
- **AND** 所有金额 MUST 由分转换为元展示
- **AND** 页面 MUST 提供刷新操作

#### Scenario: 非管理员访问

- **WHEN** 非管理员访问大盘
- **THEN** 前端 MUST 阻止页面渲染数据请求
- **AND** 后端返回 403 时 MUST 展示无权限提示

### Requirement: 入驻审核

系统 SHALL 提供入驻审核列表与通过/驳回操作，并支持查看全部凭证。

#### Scenario: 查看待审列表

- **WHEN** 管理员打开 `/admin/audits`
- **THEN** 前端 MUST 分页调用 `GET /api/admin/audit/list` 展示申请人资料与技能
- **AND** 段位截图 MUST 可查看大图，原声 MUST 可播放，未录制 MUST 明确标注
- **AND** 页面 MUST 支持分页切换

#### Scenario: 通过申请

- **WHEN** 管理员点击通过并确认
- **THEN** 前端 MUST 调用 `POST /api/admin/audit/decision` 提交通过结果
- **AND** 成功后 MUST 从待审列表移除该申请并提示成功

#### Scenario: 驳回申请

- **WHEN** 管理员选择驳回
- **THEN** 前端 MUST 要求填写驳回原因后才允许提交
- **AND** 提交成功后 MUST 刷新列表

### Requirement: 提现审核

系统 SHALL 提供提现申请列表与审核操作，并按状态筛选。

#### Scenario: 查看提现申请

- **WHEN** 管理员打开 `/admin/withdrawals`
- **THEN** 前端 MUST 调用 `GET /api/wallet/admin/withdraw/list` 展示申请人、金额、手续费、实际打款与状态
- **AND** MUST 支持按待审核、打款中、成功、驳回筛选

#### Scenario: 审核提现

- **WHEN** 管理员通过或驳回一笔待审核提现
- **THEN** 前端 MUST 调用 `POST /api/wallet/admin/withdraw/audit`
- **AND** 驳回时 MUST 要求填写原因
- **AND** 成功后 MUST 刷新列表与相关统计

### Requirement: 运营待办队列

系统 SHALL 展示待接单订单与待通知事件，并支持标记通知完成。

#### Scenario: 查看待接单订单

- **WHEN** 管理员打开待接单队列
- **THEN** 前端 MUST 分页调用 `GET /api/admin/order/pending-accept` 展示订单号、金额、下单时间与相关用户
- **AND** MUST 展示已等待时长，便于识别即将超时订单

#### Scenario: 处理待通知事件

- **WHEN** 管理员打开通知队列并确认已线下通知
- **THEN** 前端 MUST 调用 `POST /api/admin/notice/ack`
- **AND** 成功后 MUST 从待通知列表移除该条记录

### Requirement: 平台热配置

系统 SHALL 提供抽佣、支付模式、通道费率与微信商户参数的读取和保存，并保护密钥回显。

#### Scenario: 读取配置

- **WHEN** 管理员打开 `/admin/settings`
- **THEN** 前端 MUST 调用 `GET /api/admin/config/get` 回填支付模式、抽佣比例、通道费率等字段
- **AND** 密钥类字段 MUST 只展示是否已配置，不得在界面明文回显完整值

#### Scenario: 保存配置

- **WHEN** 管理员修改抽佣比例或支付模式并保存
- **THEN** 前端 MUST 校验比例范围后调用 `POST /api/admin/config/save`
- **AND** 成功后 MUST 提示配置已即时生效
- **AND** MUST NOT 要求重启后端
