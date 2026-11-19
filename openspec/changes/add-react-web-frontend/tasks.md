## 1. 工程脚手架

- [x] 1.1 创建 `web/` 目录，初始化 `package.json`（React 18、TypeScript、Vite 5、React Router 6、TanStack Query 5、Tailwind、shadcn 依赖、lucide-react、sonner、react-hook-form、zod、vitest）
- [x] 1.2 配置 `vite.config.ts`：端口 5174、`@` 路径别名、`/api` 代理到 `http://localhost:8088`
- [x] 1.3 配置 `tsconfig.json` / `tsconfig.node.json` 与路径别名，确保 `tsc --noEmit` 通过
- [x] 1.4 配置 Tailwind 与全局样式：品牌色 `#6366F1`、背景 `#F8F9FA`、圆角与字体基线
- [x] 1.5 初始化 shadcn/ui（`components.json`、CSS 变量），生成 button、input、card、dialog、dropdown-menu、table、tabs、badge、select、textarea、skeleton、sonner 等实际用到的组件
- [x] 1.6 建立目录骨架：`src/{api,hooks,lib,components/{ui,layout,domain},pages/{auth,client,order,chat,me,companion,admin},types}`
- [x] 1.7 配置 `.env.example`（`VITE_API_BASE_URL=/api`、`VITE_APP_TITLE=开黑搭子`）与 `.gitignore`
- [x] 1.8 验证：`npm run dev` 可启动空白页，`npm run build` 成功产出 `dist/`

## 2. API 客户端、类型与会话

- [x] 2.1 在 `src/types/api.ts` 定义公共响应、分页、用户、陪练、技能、订单、支付、聊天、评价、钱包、提现、管理大盘、审核、通知、平台配置类型
- [x] 2.2 实现 `lib/money.ts`：`fenToYuan`、`yuanToFen`、`formatMoney`，并在类型注释中标注各字段单位
- [x] 2.3 实现 `lib/datetime.ts`：解析后端 `LocalDateTime`、格式化、剩余时间倒计时
- [x] 2.4 实现 `lib/orderStatus.ts`：0~8 状态文案、色调、客户/陪练可执行动作映射
- [x] 2.5 实现 `api/client.ts`：fetch 封装、10s 超时、`satoken` 注入、`{code,message,data}` 解包、`ApiError` 归一化、网络错误分类
- [x] 2.6 在 `api/client.ts` 实现 401 单飞重登：共享 Promise、重放原请求一次、失败清会话并跳转 `/login`
- [x] 2.7 实现 `api/auth.ts`：`wxLogin(code, deviceType)`、`getUserInfo()`、`mockSwitchRole(role)`
- [x] 2.8 实现 `SessionProvider` 与 `useSession`：localStorage 键 `gc.web.session`、启动校验、登录/登出、角色刷新
- [x] 2.9 实现三身份登录流程：客户/陪练/管理员分别使用 `mock_web_client`、`mock_web_companion`、`mock_web_admin`，必要时调用角色切换并重新拉取用户信息
- [x] 2.10 处理 `platform.dev-tools-enabled=false`：展示配置指引弹窗，说明开关与重启步骤
- [x] 2.11 配置 TanStack Query：默认 staleTime、重试策略、`queryClient.ts` 与统一 `keys.ts`
- [x] 2.12 配置 sonner 全局 Toaster 与统一错误提示函数

## 3. 路由、布局与通用组件

- [x] 3.1 实现 `createBrowserRouter` 路由表与路由级懒加载，未匹配路由跳 404 页
- [x] 3.2 实现 `AuthGuard`：未登录跳 `/login` 并记录回跳地址
- [x] 3.3 实现 `RoleGuard`：按客户/陪练/管理员限制访问，403 展示无权限页
- [x] 3.4 实现 `AppShell`：品牌区、主导航（找陪练/我的订单/个人中心）、用户菜单、开发态身份切换
- [x] 3.5 实现 `AdminShell`：固定侧边栏（大盘/审核/提现/订单/通知/设置）、面包屑、内容区
- [x] 3.6 实现窄屏适配：导航抽屉、表格转卡片或横向滚动
- [x] 3.7 实现通用组件 `PageHeader`、`EmptyState`、`ErrorState`、`LoadingSkeleton`、`ConfirmDialog`
- [x] 3.8 实现领域组件 `Money`、`OrderStatusBadge`、`Countdown`、`AudioPreview`、`ImageUploader`、`UserAvatar`
- [x] 3.9 实现 `ImageUploader` / `file.ts`：multipart 上传、显式携带 `satoken`、进度与失败提示
- [x] 3.10 验证：三角色登录后分别落在正确首页，越权路由被拦截（已由 routeUtils / Guards 单测覆盖）

## 4. 客户端页面

- [x] 4.1 实现 `/companions` 陪练列表：调用 `GET /companion/list`、游戏筛选、在线/服务中状态、评分与接单量、隐藏本人卡片
- [x] 4.2 实现 `/companions/:userId` 详情页：技能列表（`/companion/skills`）、原声试听、评价分页（`/order/review/companion/:id`）
- [x] 4.3 详情页提供「先聊两句」入口：创建咨询订单或跳转订单聊天（按现有接口能力实现），未登录先登录再回跳
- [x] 4.4 实现 `/orders/new` 确认订单页：技能/数量/区服/账号/备注，单价 × 数量实时计算，提交 `POST /order/create`
- [x] 4.5 下单页校验必填、数量下限与「不能预约自己」，阻止非法提交
- [x] 4.6 实现支付结果分支：mock 已支付直接跳详情；微信五要素展示「请在小程序完成支付」提示
- [x] 4.7 实现 `/orders` 列表：客户/陪练视角切换、状态筛选、分页、5s 轮询、待接单倒计时
- [x] 4.8 实现 `/orders/:orderSn` 详情：订单信息、金额三元组、支付流水、状态时间轴、5s 轮询（仅在活动状态）
- [x] 4.9 实现客户操作区：支付/模拟支付、撤回、确认完结、发起维权、评价入口，按状态与角色显隐
- [x] 4.10 实现陪练操作区：接单、申请完结（含凭证上传），按状态显隐
- [x] 4.11 实现 `/chat/:orderSn`：历史消息、3s 轮询、乐观发送、失败重试、快捷话术、游戏 ID 复制、离开页面停止轮询
- [x] 4.12 实现评价表单：1~5 星、技能/态度分、标签、留言，提交 `POST /order/review/submit`
- [x] 4.13 实现 `/me`：资料展示、昵称修改、头像上传、手机号绑定、角色相关入口
- [x] 4.14 实现 `/me/wallet` 钱包页：余额、流水分页、提现记录（复用钱包 API）
- [x] 4.15 验证：客户从浏览到下单、mock 支付、接单后聊天、确认完结、评价全流程跑通（API + Playwright 实测通过）

## 5. 陪练端

- [x] 5.1 实现 `/companion/apply`：真实姓名、手机号、简介、多技能动态表单、截图上传、原声选填
- [x] 5.2 表单校验：必填项、至少一个技能、单价/单位合法性；提交 `POST /companion/apply`
- [x] 5.3 实现 `/companion/status`：未申请/审核中/已驳回/已通过四态分流，展示驳回原因与重新申请
- [x] 5.4 实现 `/companion/workbench`：工作台统计、在线开关、待接单/服务中订单区块
- [x] 5.5 接入 `GET /companion/my-status` 作为工作台守卫，未通过审核不得进入
- [x] 5.6 实现接单与申请完结操作，成功后刷新工作台与订单查询
- [x] 5.7 实现 `/companion/wallet`：可用余额、累计收益、流水、提现记录、提现申请（分提交、≥100 分、≤余额）
- [x] 5.8 验证：陪练身份申请入驻（或使用已通过账号）→ 上线 → 接单 → 申请完结 → 提现申请（API + Playwright 实测通过）

## 6. 管理端

- [x] 6.1 实现 `/admin` 经营大盘：今日订单/流水/毛抽佣/通道费/净利、在线陪练、待审数、刷新
- [x] 6.2 实现 `/admin/audits` 入驻审核列表：分页、申请人信息、多技能、截图大图、音频播放、未录制标识
- [x] 6.3 实现审核决策：通过需确认，驳回必填原因，提交 `POST /admin/audit/decision` 后刷新
- [x] 6.4 实现 `/admin/withdrawals`：状态筛选、分页、金额/手续费/实际打款、审核通过/驳回（驳回必填原因）
- [x] 6.5 实现 `/admin/orders` 待接单队列：分页、订单金额、下单时间、已等待时长、超时风险标识
- [x] 6.6 实现 `/admin/notices` 待通知队列：列表、确认已通知 `POST /admin/notice/ack`、刷新
- [x] 6.7 实现 `/admin/settings`：读取 `GET /admin/config/get`，回填支付模式、抽佣比例、通道费率、AppID、MchId、模板 ID
- [x] 6.8 密钥类字段（AppSecret、APIv3Key、商户私钥）只显示配置状态，留空表示不修改
- [x] 6.9 实现配置校验（比例范围、必填联动）与保存 `POST /admin/config/save`，提示即时生效
- [x] 6.10 验证：管理员完成一次审核、一次提现审核、一次通知确认与一次热配置保存（API + Playwright 实测通过）

## 7. 质量、文档与交付

- [x] 7.1 编写 `lib/money`、`lib/orderStatus`、`lib/datetime` 的 Vitest 单测
- [x] 7.2 编写 `api/client` 的 401 单飞重登与错误归一化单测（mock fetch）
- [x] 7.3 检查所有列表页 loading/empty/error 三态与所有 mutation 的禁用/成功/失败反馈
- [x] 7.4 检查页面轮询在卸载、失焦与状态不再活动时停止
- [x] 7.5 全量执行 `npm run build` 与 `npm run test`，修复类型与构建错误
- [x] 7.6 在 README 增加 Web 端启动、端口、代理、身份登录与构建部署说明
- [x] 7.7 手动回归三角色主流程并记录已知限制（Playwright 三角色回归完成；已知限制见 README 第十三章）