# 设计：React Web 前端

## Context

仓库现有两个可运行单元：

- `server/`：Spring Boot 3.2.5 + Sa-Token，所有接口挂在 `/api` 下，统一返回 `{ code, message, data }`，身份由请求头 `satoken` 解析，未登录返回 HTTP 200 + 业务码 401。
- `miniapp/`：uni-app（Vue3 + TS + Vite），已覆盖客户、陪练、管理员三条业务线。

后端能力已经完整，Web 端不需要新增业务接口。当前缺口是「桌面浏览器入口」：运营审核、财务对账、热配置、键盘录入场景都在手机上完成，体验和效率不足。

约束：

1. 不改 `server/` 与 `miniapp/`，Web 端作为第三个独立工程；
2. 必须复用现有鉴权与业务状态机，不能出现第二套身份或金额口径；
3. 后端已有全局 CORS（允许任意来源 + 凭证），但开发期仍走 Vite 代理，避免 Cookie/Origin 差异；
4. 后端只有微信 `wx.login` 换码登录，Web 端在开发/演示阶段复用其 `mock_` 降级分支。

## Goals / Non-Goals

**Goals:**

- 一套 React SPA 同时服务客户、陪练、管理员三种角色，覆盖小程序已有的核心闭环。
- 桌面优先、窄屏可用；管理端具备表格、批量查看凭证、表单配置等桌面级交互。
- 复用全部现有 API、`satoken` 会话、订单状态机与金额字段，不产生第二套业务规则。
- 开发态零后端改造即可登录、切换角色、跑通全流程。
- 代码结构清晰，API 层、状态映射、金额格式化可单测。

**Non-Goals:**

- 真实微信 OAuth / 扫码 / 手机号验证码登录（后续独立变更）。
- 微信 JSAPI 支付在浏览器内调起；Web 端只支持后端 `mock` 支付模式。
- WebSocket / SSE 实时推送，聊天与订单沿用轮询。
- SSR、SEO、PWA、国际化。
- 新增报表聚合接口或图表数据源；管理大盘只展示 `/admin/dashboard/summary` 已有字段。

## Decisions

### 1. 独立 `web/` 工程，而非并入 miniapp 或改造成 monorepo

选择：`web/` 自带 `package.json`、`vite.config.ts`、`tsconfig.json`，与 `server/`、`miniapp/` 平级。

理由：uni-app 的编译链与路由模型不适合桌面后台；独立工程可以自由选择路由、UI 库和构建产物，也不会污染小程序的依赖与构建缓存。仓库目前没有 workspace 基础，引入 pnpm/npm workspace 收益低、迁移成本高。

备选：在 `miniapp` 内加 H5 页面（受 uni-app 组件与路由限制，后台表格体验差）；monorepo 统一依赖（本次无必要）。

### 2. 技术栈：Vite + React 18 + TypeScript + Tailwind + shadcn/ui + TanStack Query

- **构建**：Vite 5，端口 `5174`，`/api` 代理到 `http://localhost:8088`。
- **路由**：React Router v6（`createBrowserRouter`），路由级懒加载。
- **服务端状态**：TanStack Query v5，统一缓存、失效、轮询与 loading/error 状态；不引入 Redux。
- **客户端状态**：React Context 保存会话（token、userId、role、persona），其余局部 state。
- **样式**：Tailwind CSS + shadcn/ui（Radix 原语），用 CSS 变量定义品牌色；图标用 `lucide-react`。
- **表单**：React Hook Form + zod（入驻申请、热配置、评价、提现）。
- **提示**：sonner（Toast）、自建 ConfirmDialog（替代 `window.confirm`）。
- **测试**：Vitest + React Testing Library，覆盖金额换算、状态映射、API 客户端 401 重试。

理由：shadcn/ui 输出源码、可定制，适合同时做消费端卡片流和后台表格；TanStack Query 天然适配现有「多页面轮询 + 操作后刷新」的模式。

### 3. 鉴权：复用 `wx-login` 的 mock 分支 + 开发态角色切换

**登录流程：**

1. 登录页提供三个身份入口：客户、陪练、管理员。
2. 分别提交固定 code：`mock_web_client`、`mock_web_companion`、`mock_web_admin`，`deviceType: "web"`，调用 `POST /user/wx-login`。
3. 后端 `resolveDevOpenId` 按 code 的 MD5 生成稳定 `dev_openid_*`，三个身份对应三个独立账号，互不串数据。
4. 新账号默认角色为客户；若所选身份需要陪练(2)或管理员(9)，登录后调用 `POST /user/mock-switch-role?role=X`，成功后重新拉取 `GET /user/info`。
5. 把 `{ token, userId, role, persona }` 写入 `localStorage["gc.web.session"]`。

**会话与 401 自愈：**

- 所有请求显式带 `satoken` 头。
- 收到业务码 401 或 HTTP 401 时，用保存的 persona code 做**单飞**重登（并发请求共享同一个 Promise），成功后重放原请求一次；重登失败则清空会话并跳转 `/login`。
- 401 处理只在 API 客户端做一次，避免像小程序那样在拦截器里散落 UI 逻辑。

**角色切换（开发工具）：**

- 顶栏提供「切换身份」菜单，仅在 `import.meta.env.DEV` 显示，调用 `mock-switch-role` 后刷新会话与查询缓存。
- 后端 `platform.dev-tools-enabled` 默认 `false`。若切换被拒绝，弹出说明卡片：需要在 `application.yml` 或环境变量中开启后重启，而不是只报「403」。

**权限守卫：**

| 路由前缀 | 允许角色 | 额外校验 |
| --- | --- | --- |
| `/login` | 匿名 | 已登录则跳首页 |
| `/companions`、`/companions/:userId` | 任意（列表本身公开） | 下单动作要求登录 |
| `/orders*`、`/me*` | 1 / 2 / 9 | 后端仍按会话校验归属 |
| `/companion/*` | 2 / 9 | `/companion/my-status` 的 `canAcceptOrder`，未通过则引导到申请/审核页 |
| `/admin/*` | 9 | 无 |

前端守卫只负责导航体验，最终权限始终由后端 `AuthGuard` 决定；越权返回 403 时展示无权限页，不静默吞掉。

### 4. 路由与页面结构

```text
/login

/                          → 重定向 /companions
/companions                找陪练（游戏筛选、在线状态、试听、评分）
/companions/:userId        陪练详情（技能列表、评价分页、先聊两句/去下单）
/orders/new                确认下单（技能、数量、区服、账号、备注、金额联动）
/orders                    我的订单（客户视角 / 陪练视角 Tab、状态筛选、分页）
/orders/:orderSn           订单详情（时间轴、倒计时、操作区、支付流水、聊天入口、评价）
/chat/:orderSn             订单聊天室（轮询、快捷话术、游戏 ID 复制）
/chat/consult/:companionUserId  咨询聊天室（未下单先聊两句，房间号 CONSULT_min_max）
/me                        个人中心（资料编辑、头像上传、手机号、角色入口）
/me/wallet                 钱包流水与提现记录（陪练/管理员）

/companion/apply           入驻申请（多游戏技能 + 段位截图 + 原声加分项）
/companion/status          审核中 / 已驳回（含驳回原因、重新申请入口）
/companion/workbench       接单工作台（在线开关、待接单、服务中、收益、提现）
/companion/wallet          收益与提现（余额、提现申请、流水）

/admin                     经营大盘（今日订单/流水/毛抽佣/通道费/净利/在线人数/待审数）
/admin/audits              入驻审核（凭证大图、音频试听、通过/驳回）
/admin/withdrawals         提现审核（待审/打款中/成功/驳回）
/admin/orders              待接单订单与超时状态
/admin/notices             待通知队列（确认已通知）
/admin/settings            平台热配置（抽佣、支付模式、商户参数、模板 ID）
```

布局：

- 通用 `AppShell`：左侧品牌 + 顶部主导航（找陪练 / 我的订单 / 个人中心）+ 右侧用户菜单；内容最大宽度 `1200px`。
- `AdminShell`：左侧固定侧边栏（大盘、审核、提现、订单、通知、设置），顶部面包屑；内容区可滚动。
- 窄屏（< 768px）：导航折叠为抽屉，表格切换为卡片列表；不追求与小程序一致，只保证可操作。

### 5. API 客户端与数据层

目录：

```text
web/src/
  api/
    client.ts        // fetch 封装、超时、401 单飞重登、错误归一化
    auth.ts          // wx-login、info、mock-switch-role
    game.ts          // 品类字典
    companion.ts     // 列表、技能、申请、状态、工作台、上下线
    order.ts         // 下单、详情、列表、支付/模拟支付、接单、申请完结、确认、撤单、维权
    chat.ts
    review.ts
    wallet.ts        // 提现申请/记录/流水
    admin.ts         // 大盘、审核、提现审核、待接单、通知、配置
    file.ts          // multipart 上传
  hooks/             // useSession、useOrderPolling、useCompanionStatus...
  lib/
    money.ts         // 分/元转换与格式化
    orderStatus.ts   // 0~8 文案、色调、可执行动作
    datetime.ts      // LocalDateTime 解析、倒计时
    queryClient.ts
  components/
    ui/              // shadcn 生成组件
    layout/          // AppShell、AdminShell、AuthGuard、RoleGuard
    domain/          // CompanionCard、OrderStatusBadge、OrderTimeline、Money、AudioPreview、ImageUploader
  pages/
    auth/ client/ order/ chat/ me/ companion/ admin/
  types/api.ts       // 手写 DTO/VO 类型（仅覆盖用到的字段）
```

约定：

- `client.ts` 返回 `data`，非 200 抛 `ApiError { code, message, status }`；网络错误单独归类。
- Base URL：`import.meta.env.VITE_API_BASE_URL ?? "/api"`。
- 超时 10s（上传除外），用 `AbortController`。
- Query key 统一由 `keys.ts` 生成，mutation 成功后精确失效相关 key。
- 轮询：订单列表 5s、订单详情 5s（仅在状态 1/2/3/7）、聊天 3s；页面失焦或组件卸载即停。

**金额口径（易错点）：**

- 订单金额、钱包、清算、提现、工作台、`CompanionSkillVO.price` 均为**分**。
- `CompanionItemVO.price` 是**元**（整数）。
- 统一在边界转换：API 类型保留原值，展示层用 `fenToYuan()`；下单提交用 `yuanToFen()`。禁止在组件内散落 `/100`。

**订单状态映射**（来自 `OrderStatusEnum`）：

| code | 文案 | 主色 | 客户可操作 | 陪练可操作 |
| --- | --- | --- | --- | --- |
| 0 | 待付款 | amber | 支付 / 取消 | — |
| 1 | 已付款待接单 | blue | 撤回（退款） | 接单 |
| 2 | 服务中 | indigo | 发起维权 | 申请完结 |
| 3 | 申请完结 | violet | 确认完结 / 维权 | 等待确认 |
| 4 | 已完成 | green | 评价 | — |
| 5 | 已取消 | gray | — | — |
| 6 | 已退款 | gray | — | — |
| 7 | 维权争议中 | red | 等待仲裁 | 等待仲裁 |
| 8 | 仲裁退款完成 | gray | — | — |

### 6. 关键业务交互

- **下单**：从陪练卡片带 `companionUserId + skillId` 进入，拉 `/companion/skills` 校验价格与单位；数量 × 单价实时计算；提交 `/order/create`。
- **支付**：下单返回 `PaymentResultVO`。若 `payType === "mock"` 或 `isMockPaid`，直接跳订单详情；若返回微信五要素，展示「当前为微信支付模式，请在微信小程序完成支付」说明，不伪造支付成功。订单详情另提供 `mock-pay` 按钮，仅在后端 mock 模式下显示。
- **倒计时**：待接单用 `createTime + 10min`（后端默认阈值）计算；后端超时退款后轮询自动刷新。前端只做提示，不代替后端调度。
- **聊天**：进入页面拉全量消息，之后 3s 轮询；发送后乐观插入并显示发送中，失败标红可重试；`senderId` 与当前 userId 比对决定左右气泡。
- **入驻申请**：多技能动态表单；段位截图与音频走 `POST /file/upload`（显式带 `satoken`），返回 URL 存入 `rankImg` / `voiceUrl`；音频为选填，未录制不伪造。
- **审核**：图片用 `/file/download/{fileName}` 预览，音频用原生 `<audio controls>`；通过/驳回调用 `/admin/audit/decision`，驳回必填原因。
- **提现**：金额以分提交（最低 100 分），展示可用余额；审核通过/驳回调用 `/wallet/admin/withdraw/audit`。
- **热配置**：表单加载 `/admin/config/get`；密钥类字段只展示已配置状态，不回显完整值；保存后提示「已即时生效」。

### 7. 视觉与体验基线

- 品牌色沿用小程序：主色 `#6366F1`（indigo），背景 `#F8F9FA`，卡片白色 + 轻阴影，圆角 `12px`。
- 统一组件：`PageHeader`、`EmptyState`、`ErrorState`、`Skeleton`、`Money`、`StatusBadge`、`ConfirmDialog`。
- 所有列表具备 loading / empty / error 三态；所有 mutation 有禁用态与成功/失败提示。
- 时间统一 `YYYY-MM-DD HH:mm`；金额两位小数、千分位。
- 可访问性：按钮有可读文本、对话框焦点管理、表单 label 关联。

### 8. 构建与部署

- `npm run dev` → `http://localhost:5174`，代理 `/api`。
- `npm run build` → `web/dist` 静态资源；`npm run preview` 本地验证产物。
- 生产推荐 Nginx：静态资源 + `/api` 反向代理到后端同源，避免 CORS；也可由 Spring Boot 静态目录托管，但需处理 SPA history fallback。
- 环境变量：`VITE_API_BASE_URL`（默认 `/api`）、`VITE_APP_TITLE`。
- 不引入 Docker 改动；在 README 增加 Web 启动说明。

## Risks / Trade-offs

- **[mock 登录不是生产方案]** → 后端开发降级分支在真实小程序配置下仍会为 `mock_` code 建档。Web 端把 mock 入口标记为开发模式，并在登录页显著提示；生产 Web 登录需后续单独设计与加固。
- **[角色切换依赖 dev-tools]** → 默认关闭，首次启动可能卡在「无权限」。用专门提示卡片给出配置项与重启步骤，避免用户误判为登录失败。
- **[Web 无法完成微信支付]** → 明确降级为「引导去小程序支付」，不做假成功；演示与联调统一使用 mock 支付模式。
- **[后端金额单位不统一]** → 在类型层注释 + 边界函数约束，单测覆盖 `CompanionItemVO.price` 的元口径。
- **[轮询带来无效请求]** → 页面可见性 + 状态条件控制，离开页面即停；后续可升级 SSE。
- **[与小程序功能漂移]** → 本次只做已有能力，状态映射抽成单一模块；后端新增状态时只需改一处并补测试。
- **[shadcn 组件体积]** → 只生成实际使用的组件，路由级代码分割。

## Migration Plan

1. 新增 `web/` 工程与依赖，不改现有目录。
2. 完成 shell、登录、API 客户端与守卫，验证三角色可登录。
3. 按客户 → 陪练 → 管理端顺序交付页面；每阶段可独立构建。
4. 在 README 增加启动/构建/代理说明；不触碰现有部署脚本。
5. 回滚：删除 `web/` 或不下发静态资源即可，后端与小程序零影响。

## Open Questions

- 生产环境 Web 登录采用哪种方式（微信开放平台扫码 / 手机号验证码 / 管理员账密）？本次不实现。
- 管理端是否需要按日期区间看流水与导出 CSV？现有接口只有「今日」汇总，需后端新增聚合接口后再做。
- 聊天是否需要已读、未读数与全局消息入口？当前只有订单内消息接口。
- 文件上传大小与类型限制是否需要在 Web 端二次校验（后端限制待确认）。
