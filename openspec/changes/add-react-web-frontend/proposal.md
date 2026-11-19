# React Web 前端

## Why

现有产品只有微信小程序一个入口：桌面浏览器无法使用，运营审核、提现打款、财务对账和热配置只能在手机上操作，效率低且容易误触；对外演示和分享也受微信生态限制。后端已经提供了完整的 REST API（客户 / 陪练 / 管理员三套能力），增加一个 React Web 端可以零后端改造地打开桌面入口，复用同一套业务状态机、资金清算与鉴权规则。

## What Changes

- 新增独立工程 `web/`：**Vite + React 18 + TypeScript + Tailwind CSS + shadcn/ui**，与现有 `server/`、`miniapp/` 并列，不改动二者。
- 覆盖三类角色：
  - 客户端：找陪练、陪练技能与原声试听、下单支付、订单列表/详情、倒计时与撤单、确认完结、售后维权、评价、站内聊天、个人中心；
  - 陪练端：入驻申请（多游戏技能）、审核状态四态分流、接单工作台、上下线开关、接单/申请完结、收益与提现；
  - 管理端：经营大盘、入驻审核、提现审核、待接单/待通知、平台热配置。
- 统一 API 客户端：`fetch` 对接 `/api`，沿用 `satoken` 请求头与 `{code, message, data}` 响应约定，业务码 401 时自动静默重登并原地重试，其余业务错误统一 Toast。
- 开发态登录：调用现有 `POST /user/wx-login`，提交稳定的 `mock_` 前缀 code 自动建档；登录页提供「客户 / 陪练 / 管理员」身份入口，通过现有 `POST /user/mock-switch-role` 切换当前账号角色。
- 桌面优先布局：顶部导航 + 内容区，管理端使用侧边栏；窄屏响应式降级，保证笔记本与小屏可用。
- 金额统一「分 → 元」展示，订单状态徽章、审核状态、提现状态与后端枚举一一对应。
- **Non-goals**：
  - 不修改 `server/` 接口、白名单与鉴权策略；
  - 不实现真实微信 OAuth / 扫码登录、短信登录（属于后续独立变更）；
  - 不做 SSR / SEO，不替代小程序；
  - 不引入 WebSocket，聊天沿用轮询；
  - 不新增业务能力，只做已有 API 的桌面呈现。

## Capabilities

### New Capabilities

- `web-app-shell`：Web 工程脚手架、设计令牌与主题、路由与布局、会话持久化、mock 登录与角色守卫、统一 API 客户端与错误处理。
- `web-client-experience`：客户侧浏览、下单支付、订单全流程、聊天、评价与个人资料。
- `web-companion-workspace`：陪练入驻、审核状态、接单工作台、履约操作、收益与提现。
- `web-admin-console`：经营大盘、审核工作流、待办队列与平台热配置。

### Modified Capabilities

无。后端鉴权白名单、订单状态机与热配置行为均不变化，无需修改现有 spec。

## Impact

- 新增代码：`web/`（独立 `package.json`、Vite 配置、`src/`），新增依赖 React、React Router、Tailwind、shadcn/ui 相关包、TanStack Query、zod、sonner 等。
- 复用后端接口（只读清单）：`/user/*`、`/companion/*`、`/order/*`、`/order/review/*`、`/chat/*`、`/wallet/*`、`/admin/*`、`/notice/*`、`/file/download/*`、`/health`。
- 本地开发端口 `5174`，Vite 代理 `/api` → `http://localhost:8088`，避免跨域和端口冲突。
- 开发态角色切换依赖后端 `platform.dev-tools-enabled=true`；未开启时 Web 端给出明确提示，不影响生产构建。
- 不影响小程序发布与后端部署；Web 端可独立构建为静态资源，由 Nginx 或后端静态目录托管（部署细节见 design）。
