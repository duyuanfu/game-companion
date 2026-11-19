## ADDED Requirements

### Requirement: 独立的 Web 构建与运行配置

系统 SHALL 以独立 `web/` 工程提供 React SPA，可单独安装依赖、启动开发服务器并产出静态构建物。

#### Scenario: 本地启动开发服务器

- **WHEN** 开发者在 `web/` 下执行 `npm run dev`
- **THEN** 应用 MUST 在 `http://localhost:5174` 启动
- **AND** 所有以 `/api` 开头的请求 MUST 被代理到 `http://localhost:8088`
- **AND** 启动过程 MUST NOT 修改 `server/` 或 `miniapp/` 下的任何文件

#### Scenario: 生产构建

- **WHEN** 执行 `npm run build`
- **THEN** MUST 在 `web/dist` 产出可直接由静态服务器托管的资源
- **AND** API 基地址 MUST 可由 `VITE_API_BASE_URL` 覆盖，未设置时默认使用同源 `/api`

### Requirement: 开发态身份入口

系统 SHALL 在登录页提供客户、陪练、管理员三种身份入口，并通过现有 `POST /api/user/wx-login` 的 `mock_` 降级分支建立互不串数据的独立账号。

#### Scenario: 以客户身份进入

- **WHEN** 用户选择「客户」并点击进入
- **THEN** 前端 MUST 以 `code=mock_web_client`、`deviceType=web` 调用登录接口
- **AND** 登录成功后 MUST 保存 `token`、`userId`、`role`、`persona`
- **AND** 当前角色 MUST 为客户(1)

#### Scenario: 以陪练或管理员身份进入

- **WHEN** 用户选择「陪练」或「管理员」并登录成功
- **THEN** 若当前账号角色与所选身份不一致，前端 MUST 调用 `POST /api/user/mock-switch-role` 切换到目标角色(2 或 9)
- **AND** 切换成功后 MUST 重新拉取 `GET /api/user/info` 并以最新角色刷新会话
- **AND** 三个身份 MUST 使用各自独立的 mock code，互不共享账号与订单数据

#### Scenario: 开发工具未开启

- **WHEN** 角色切换接口返回被拒绝（`platform.dev-tools-enabled` 未开启）
- **THEN** 前端 MUST 展示明确的配置指引，说明需要开启该开关并重启后端
- **AND** MUST NOT 将失败伪装成登录成功或静默跳转

### Requirement: 会话持久化与 401 自动恢复

系统 SHALL 将会话持久化到浏览器本地，并在凭证失效时自动重登后重放原请求一次。

#### Scenario: 刷新页面恢复会话

- **WHEN** 已登录用户刷新浏览器
- **THEN** 前端 MUST 从本地存储恢复 token 并调用 `GET /api/user/info` 校验
- **AND** 校验通过后 MUST 停留在当前受保护路由，无需重新选择身份

#### Scenario: 凭证过期

- **WHEN** 任意受保护请求返回业务码 401
- **THEN** 前端 MUST 使用当前 persona 的 mock code 重新登录，且并发请求 MUST 共享同一次重登
- **AND** 重登成功后 MUST 自动重放原请求一次
- **AND** 重登失败后 MUST 清空会话并跳转 `/login`，提示登录已失效

### Requirement: 基于角色的路由守卫

系统 SHALL 按当前登录用户角色限制页面访问，同时保持后端为最终鉴权方。

#### Scenario: 客户访问管理端

- **WHEN** 角色为客户的用户直接访问 `/admin` 下任意路由
- **THEN** 前端 MUST 拦截并展示无权限页面或跳转到客户首页
- **AND** MUST NOT 发起管理端数据请求

#### Scenario: 未通过审核的陪练访问工作台

- **WHEN** 角色为陪练但 `GET /api/companion/my-status` 返回 `canAcceptOrder=false`
- **THEN** 访问 `/companion/workbench` MUST 被引导到审核状态页或入驻申请页
- **AND** 页面 MUST 展示审核中、已驳回或未申请的对应状态与原因

#### Scenario: 后端返回越权

- **WHEN** 任意请求返回 403
- **THEN** 前端 MUST 展示无权限提示
- **AND** MUST NOT 通过隐藏错误或重复重试绕过

### Requirement: 统一的数据展示规范

系统 SHALL 以统一规则展示金额、订单状态与时间，避免页面间口径不一致。

#### Scenario: 展示订单金额

- **WHEN** 页面渲染任意以分为单位的金额字段
- **THEN** MUST 转换为元并保留两位小数、千分位
- **AND** `CompanionItemVO.price` MUST 按元直接展示，不得再次除以 100
- **AND** 提交下单或提现金额时 MUST 按接口要求转换为分

#### Scenario: 展示订单状态

- **WHEN** 页面渲染订单状态 0 至 8
- **THEN** MUST 使用与后端 `OrderStatusEnum` 一致的文案与配色
- **AND** 不同时展示互相矛盾的兜底文案

### Requirement: 桌面优先的响应式布局

系统 SHALL 提供桌面优先的导航与布局，并在窄屏下保持核心操作可用。

#### Scenario: 桌面端浏览

- **WHEN** 视口宽度不小于 1024px
- **THEN** 通用页面 MUST 展示顶部主导航与内容区
- **AND** 管理端 MUST 展示固定侧边栏与内容区

#### Scenario: 窄屏浏览

- **WHEN** 视口宽度小于 768px
- **THEN** 主导航 MUST 折叠为抽屉或菜单
- **AND** 表格类页面 MUST 提供可操作的卡片或横向滚动替代方案
