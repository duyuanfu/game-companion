# 游戏陪玩/开黑搭子微信小程序 (Game Companion)
> 当前 Web 产品名：**PP电竞鼠**；历史文档中的“开黑搭子”为旧称。


本项目为一款游戏开黑陪练预约系统的完整商业级全栈研发仓库。

---

## 📑 目录导航
- [一、 项目核心架构与技术底座](#一-项目核心架构与技术底座)
- [二、 核心金融架构：订单费用与资金清算三层解耦体系（重点）](#二-核心金融架构订单费用与资金清算三层解耦体系重点)
- [三、 数据库 14 张核心业务表全景设计](#三-数据库-14-张核心业务表全景设计)
- [四、 落地商业级功能全景清单 (全量通过自动化单元测试)](#四-落地商业级功能全景清单-全量通过自动化单元测试)
- [五、 用户登录、手机号授权与微信身份机制](#五-用户登录手机号授权与微信身份机制)
- [六、 微信小程序账号申请、主体类目与工信部备案保姆级指南](#六-微信小程序账号申请主体类目与工信部备案保姆级指南)
- [七、 消息通知链路配置与上线检查](#七-消息通知链路配置与上线检查)
- [八、 自动化单元测试验证报告](#八-自动化单元测试验证报告)
- [九、 平台安全与鉴权模型（默认拒绝）](#九-平台安全与鉴权模型默认拒绝)
- [十、 自动化运维：订单超时调度](#十-自动化运维订单超时调度)
- [十一、 敏感配置与密钥治理](#十一-敏感配置与密钥治理)
- [十二、 上线前必做清单（Deployment Checklist）](#十二-上线前必做清单deployment-checklist)

---

## 一、 项目核心架构与技术底座

### 1. 后端工程结构 (`server/`)
基于 **JDK 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + Redis 7 + Redisson 3.28 + Sa-Token 1.38**：
* **数据持久层规范**：数据表建表脚本统一放置于 `server/src/main/resources/init_schema.sql`，支持全量初始化与自动升级补齐；
* **统一支付网关 (策略模式解耦)**：
  - 默认启用 `MockPaymentService`（开发期免商户号一键模拟付款与退款回调）；
  - 预留 `WechatV3PaymentServiceImpl`（商户号申请下发后，填入配置秒级无缝热切换真实微信支付 V3 与商家转账到零钱）；
* **通知中心 (`NoticeService`)**：微信订阅消息已下线，改为 `notice_log` 线下通知事件队列 + 管理员手机端「待通知」Tab 人工兜底（详见第四章「消息通知机制」）；
* **高并发与并发安全**：基于 **Redisson 分布式锁 + 数据库乐观锁** 保障交易与资金账户并发安全；
* **手机端管理员工作台 (`admin`)**：支持管理员在线核验段位截图、试听原声音频、一键审批/驳回入驻申请、审批提现打款、售后维权仲裁及秒级热配置经营参数。

### 2. 前端小程序工程 (`miniapp/`)
基于 **Uni-app (Vue3 + TypeScript + Vite + Pinia)**：
* `pages/index/index.vue`：精选陪练师主页、游戏品类筛选、**本地原生音频秒播试听控制条**、屏蔽自身卡片（防刷单）、“先聊两句”在线咨询；
* `pages/order/create.vue`：确认预约下单，支持多游戏横向卡片切换、单价与工时动态联动计算、一键模拟支付；
* `pages/order/list.vue` & `detail.vue`：订单全流程时间轴（Timeline）、10分钟倒计时、主动撤单、战绩查验、售后维权入口、真实评价打分；
* `pages/chat/index.vue`：开黑专属加密双向聊天室、快捷上号话术、游戏 ID 一键复制、加钟扩展插槽；
* `pages/my/index.vue`：个人中心商业化渐进式分流（普通用户/陪练师/连击5次解锁管理员暗门）；
* `pages/companion/apply.vue` & `workbench.vue`：陪练资质申请与独立接单看板（在线/勿扰切换、收益提现）；
* `pages/admin/audit.vue`：管理员手机工作台（入驻待审、提现待审、实时财务对账经营大盘、微信支付与抽成热配置）。

### 3. 本地联调端口与服务地址（极易踩坑，务必对齐）

| 项目 | 默认值 | 说明 |
| :--- | :--- | :--- |
| 后端端口 | **8088** | `server.port: ${SERVER_PORT:8088}`，可用环境变量 `SERVER_PORT` 覆盖 |
| 小程序 BASE_URL | `http://192.168.1.9:8088/api` | `miniapp/src/config/env.ts`，可用 `VITE_API_BASE_URL` 覆盖 |
| H5 调试代理 | `http://localhost:8088` | `miniapp/vite.config.ts`，可用 `VITE_API_PROXY_TARGET` 覆盖 |
| 服务自检接口 | `GET /api/health` | 返回服务标识 `app = game-companion` 与当前端口 |

> ⚠️ **为什么不用 8080？** 本机 8080 端口常被其它项目占用。一旦小程序 `BASE_URL` 指到别的服务，
> 前端会收到**那个服务**的响应（例如采购系统的 `{"code":401,"message":"请先登录或登录凭证已失效"}`），
> 表现为「点任何按钮都提示没登录」，而本项目后端根本没被访问，极难定位。

**排查口诀：后端启动日志会直接打印正确的地址：**

```text
====================================================
 游戏陪玩/开黑服务后端启动成功!
  · 本地访问地址 : http://localhost:8088/api
  · 局域网真机地址: http://192.168.1.9:8088/api
  · 小程序 BASE_URL 必须配置为: http://<本机IP>:8088/api
  · 服务自检接口: http://<本机IP>:8088/api/health
====================================================
```

**前端已内置自诊断**：当 401 自动静默重登仍然失败时，小程序会自动调用 `/health` 比对服务标识，
若发现连的不是「开黑搭子」，会直接弹窗提示**当前 BASE_URL 指向了哪个服务**以及应该改成什么，
而不是只给一句含糊的「凭证已失效」。


---

## 二、 核心金融架构：订单费用与资金清算三层解耦体系（重点）

在电商与 O2O 严肃金融级系统中，将订单金额、微信通道手续费、平台抽成、陪练师结算所得全部混在订单主表里，是典型的**“业务流与资金流混为一谈的反模式设计”**。

本项目对订单费用与财务结算进行了**严密的三层领域解耦**：

```text
┌────────────────────────────────────────────────────────────────────────┐
│ 1. 交易履约域 companion_order (纯业务订单)                             │
│    - 职责：谁买了什么、服务工时、履约进度 (纯粹业务履约态)              │
│    - 金额分层：标价(original_amount) - 优惠(discount_amount) = 实付(total_amount) │
│    - 彻底剥离底层支付细节与清算收益，未完结前绝不早产分润数据           │
└────────────────────────────────────────────────────────────────────────┘
                                    │ 发生真实支付 (1:N 映射)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 2. 资金支付事实域 pay_flow (支付流水事实表)                             │
│    - 职责：全权承载每一次向第三方支付网关 (微信支付) 发起的付款/退款事实  │
│    - 核心字段：pay_sn(内部流水), transaction_id(微信商户号单号), pay_amount │
│    - 真实通道费：channel_fee (微信0.6%手续费，仅在支付成功时落盘，未付绝不早产) │
│    - 退款冲正：记录累计退款金额 refund_amount 与微信冲正退还的通道费 refund_channel_fee │
└────────────────────────────────────────────────────────────────────────┘
                                    │ 服务完成确认打款 (状态 4)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 3. 佣金清算域 order_settlement (订单分佣清算明细表)                    │
│    - 职责：仅在订单履约圆满完结确认打款的一瞬间生成正式结算单           │
│    - 核心字段：settle_sn(清算流水号), total_amount(总成交额), companion_income(打手收益) │
│    - 平台净利核算：platform_gross_fee(毛抽成) - channel_fee(0.6%) = platform_net_profit(净利润) │
│    - 权责归属：fee_payer (标记微信通道费承担主体：1-平台承担 2-打手转嫁 3-买家附加) │
└────────────────────────────────────────────────────────────────────────┘
```

### 1. 微信官方真实的收费底牌与 0.6% 通道费
* **玩家付款 (微信 JSAPI 支付)**：
  - 微信对软件/咨询类生活服务小程序统一扣取 **0.6%（千分之六）** 通道服务费；
  - **扣费方式为底层实时截留**。玩家付款 50.00 元，实际进入商户号可结算资金池的只有 **49.70 元**，0.30 元被微信官方当场扣走。
* **打手提现 (微信商家转账到零钱)**：目前官方基本免收通道费 (0%)。

### 2. 真实财务账本推演（以一单 50 元为例，平台抽佣 10%）
$$
\begin{aligned}
\text{买家实付款 (total\_amount)} &= 50.00\text{ 元 (5000分)} \\
\text{微信官方扣款 (channel\_fee)} &= \text{round}(50.00 \times 0.6\%) = 0.30\text{ 元 (30分)} \\
\text{陪练师结算所得 (companion\_income)} &= 50.00 \times 90\% = 45.00\text{ 元 (4500分)} \\
\text{平台账面毛抽佣 (platform\_gross\_fee)} &= 50.00 \times 10\% = 5.00\text{ 元 (500分)} \\
\mathbf{平台真实到手净利润 (platform\_net\_profit)} &= 5.00 - 0.30 = \mathbf{4.70\text{ 元 (470分)}}
\end{aligned}
$$
* **防亏损运营红线**：切忌盲目开展“0% 免佣金活动”！若平台免佣，客户付 50 元，陪练师提现 50 元，商户号却被微信扣走 0.30 元，平台每成交一单就会白白倒贴 0.6%！初期让利建议抽成最低设为 `3%~5%`。
* **退款通道费冲正**：当发生订单全额撤回或接单超时自动退款时，系统在 `pay_flow` 中将支付状态标记为已退款，并自动将微信冲正退回的通道费记录到 `refund_channel_fee`，确保月末对账分毫不差。

---

## 三、 数据库 14 张核心业务表全景设计

数据表基于 **MySQL (InnoDB, utf8mb4)** 设计，**所有金额字段统一采用整型（单位：分）**，彻底避免浮点数精度截断问题。

| 序号 | 数据表名 | 业务领域 | 核心职责与关键字段说明 |
| :--- | :--- | :--- | :--- |
| **1** | `sys_user` | 用户认证域 | 买家/陪练/管理员统一账号表。包含 `openid`, `unionid`, `phone`, `role`(1客户/2陪练/9管理员)。针对微信生态 `unionid` 实施 NULL 归一化防御，杜绝唯一键冲突。 |
| **2** | `companion_profile` | 陪练资质域 | 陪练师专属资质档案。包含实名认证、原声音频地址 (`voice_url`)、接单开关 (`is_online`)、接单统计与综合评分 (`rating`)。 |
| **3** | `game_category` | 游戏品类域 | 平台支持的游戏字典表（王者荣耀、英雄联盟、和平精英、无畏契约等）。 |
| **4** | `companion_skill` | 服务定价域 | 实现**一陪多项目与差异化计价**。关联陪练档案与游戏分类，包含段位截图、单价 (`price`, 分) 与计价单位（小时/局）。 |
| **5** | `companion_order` | 交易履约域 | **纯业务交易订单主表**。包含标价 (`original_amount`)、立减 (`discount_amount`)、实付 (`total_amount`)、业务状态机 (`order_status`)、区服账号、维权信息。已彻底剔除资金流与分润字段。 |
| **6** | `pay_flow` | 资金支付域 | **支付事实流水表**。记录每次向微信发起支付的事实。包含支付单号 (`pay_sn`)、微信商户流水号 (`transaction_id`)、实付金额 (`pay_amount`)、微信0.6%扣费 (`channel_fee`) 及退款冲正 (`refund_*`)。 |
| **7** | `order_settlement` | 佣金清算域 | **订单佣金分润清算表**。履约完结确认打款那一刻正式生成。包含清算单号 (`settle_sn`)、打手收益 (`companion_income`)、平台毛佣 (`platform_gross_fee`)、通道扣费 (`channel_fee`) 与平台真实净利 (`platform_net_profit`)。 |
| **8** | `order_review` | 评价信用域 | **订单服务评价表**。买家对已完结订单打分（1~5星）、印象标签与评语，提交后自动触发重新计算陪练师均分并联动更新至 `companion_profile.rating`。 |
| **9** | `user_wallet` | 资金钱包域 | 陪练师收益账户。包含可用余额 (`balance`)、提现冻结金额 (`frozen_balance`)、累计总收益 (`total_income`)，配置了 **`version` 乐观锁版本号**，并发更新绝对安全。 |
| **10** | `wallet_flow` | 财务流水域 | 钱包资金变动金融级明细流水。每一笔订单入账、提现冻结、打款扣除、驳回解冻强制双向记账，包含前后余额快照。 |
| **11** | `withdraw_record` | 提现审批域 | 陪练师提现申请与打款记录。对接微信“商家转账到零钱”，包含申请金额、预留手续费 (`fee`)、实际打款额 (`actual_amount`)、微信批次单号 (`wechat_batch_id`) 与审核状态。 |
| **12** | `chat_message` | 即时通讯域 | 开黑专属房间即时聊天记录表。按订单号天然隔离房间，记录双方沟通消息与系统广播。 |
| **13** | `notice_log` | 通知事件域 | 平台线下通知事件队列。订单 / 审核 / 维权事件落库，供管理员「待通知」Tab 人工跟进，含当事人昵称、手机号、金额与完成时间。 |
| **14** | `sys_config` | 运营配置域 | 平台全局参数动态配置表。手机端管理员在线修改 AppID、Secret、商户号、APIv3Key、抽成比例，**秒级热生效（零重启）**。 |

---

## 四、 落地商业级功能全景清单 (全量通过自动化单元测试)

本系统对标一线商业陪练平台（比心、TT语音），已全量落地并自动化测试跑通以下核心业务闭环：

1. **全动态真实微信号独立建档**：每个微信号扫码进入小程序自动根据微信官方唯一 OpenID 建立专属档案与独立资金钱包，支持两个微信号分别作为【点单老板】与【认证陪练师】全流程端到端互测；
2. **严格防刷单闭环**：严禁陪练师预约自己（后端抛出 `ApiException` 拦截），首页列表自动彻底屏蔽陪练师本人的卡片；
3. **一陪多游戏与差异化定价**：支持同一陪练师上架多个游戏项目（王者荣耀 25元/小时、英雄联盟 30元/小时），下单页横向自由切换，单价与工时动态联动计算；
4. **先聊后买（在线咨询）**：客户付款前可在卡片点击「先聊两句」发起轻量咨询，陪练师回复后客户可直接在聊天室点击「去下单」快速转化；
5. **接单 10 分钟倒计时与超时自动全额退款**：待接单卡片动态展示 10 分钟倒计时，若陪练师超时未响应，系统自动触发原路全额退款（流转为 `已退款(6)`），支付流水自动冲正；
6. **客户待接单主动撤回 (全额退款)**：客户在待接单状态下可随时点击撤单，资金原路秒级全额退款，退款流水号全量落库；
7. **开黑专属房间真实即时双向聊天**：基于持久化消息中心，跨设备实时双向通信，内置快捷开黑组队话术与游戏 ID 一键复制，合规留存私域；
8. **加钟续单解耦扩展架构保护**：已将原有直接修改主订单工时总额的不规范逻辑解耦下线，统一升级为预留加钟独立子订单 (`addon_sub_order`) 规范扩展插槽；
9. **履约结单 -> 战绩核验 -> 确认打款**：服务完成后陪练上传战绩截图，客户确认后佣金正式通过 `order_settlement` 清算划入陪练师钱包 (`user_wallet`)，平台真实净利润同步入账；
10. **真实评价体系与陪练师评分联动**：订单完结后客户打 1~5 星好评与标签，评价真实入库 `order_review`，并自动重新计算陪练师平均分实时联动更新；
11. **售后维权争议与平台仲裁全链路**：
    - 客户在服务中或核验战绩时如遇纠纷，可发起售后维权，订单进入 `维权争议中(7)`，开黑房间插播维权广播，暂停自动结算；
    - 管理员介入仲裁：若支持买家则全额退款，流转为 `仲裁退款完成(8)` 并冲正支付流水；若驳回则恢复正常履约；
12. **钱包并发安全控制 (乐观锁版本号)**：`user_wallet` 增加 `version` 控制，每次提现、入账打款强制带版本号校验，彻底杜绝并发更新丢失风险；
13. **陪练师全额申请提现与管理员审核打款**：可用余额划入冻结池防超额提现，管理员在手机端一键批准打款（商家转账到零钱）或驳回解冻资金；
14. **手机端可视化经营看板与热配置中心**：
    - 看板直观展现：今日订单量、今日实付流水、平台毛抽佣、**微信通道扣费 (0.6%)**、**今日实际净利润**；
    - 在线修改 AppID、Secret、商户号 MchId、APIv3Key、抽成比例，一键保存秒级全网生效，无需重启后端服务。

---

## 五、 用户登录、手机号授权与微信身份机制

本项目采用微信生态推崇的**两阶段阶梯式登录**：

```text
阶段一：进入小程序【静默登录】(零感知)
├── 用户首次打开小程序，调用 wx.login() 获取临时凭证 code
├── 后端自动换取 openid，并自动生成客户档案记录 (sys_user)
└── 客户端静默完成 Sa-Token 鉴权，用户可无门槛浏览陪玩师、在线试听原声录音

阶段二：产生核心交互时【微信手机号一键授权】
├── 触发场景：用户点击「立即预约下单」或「申请成为陪玩师」
├── 交互组件：微信原生 <button open-type="getPhoneNumber">
└── 核心优势：用户无需输入密码、无需等待短信验证码，点击允许即可 1 秒完成绑定。
    真实手机号用于订单到达时的短信外呼兜底提醒，彻底杜绝漏单。
```

### 1. 开发调试期手机号模拟机制
* 本地开发测试阶段，后端 `application.yml` 预留的微信密钥是占位符（`your-miniapp-secret`）；
* 当后端换取真实手机号返回 `41002 (AppID/Secret 不匹配)` 时，后端会自动优雅降级为开发模拟号（`13800xxxxxx`）存库，确保联调顺畅；
* 生产环境填入真实密钥后，微信官方解密接口自动激活，数据库写入与微信弹窗展示号码 100% 一致。

### 2. UnionID 线上空值防崩溃机制
微信若未绑定开放平台，返回的 `unionid` 为空。后端在 `UserServiceImpl` 中内置了 `normalizeUnionid` 安全过滤，若为空串或 `"null"` 一律归一化为 Java 的 `null`，配合数据库 `DEFAULT NULL`，彻底杜绝了因空字符串触发 MySQL `uk_unionid` 唯一索引硬崩溃的问题。

### 3. 用户自助修改头像与昵称
任何登录用户（含普通客户）都可在「个人中心」自行维护展示资料：

| 能力 | 交互 | 接口 |
| :--- | :--- | :--- |
| 换头像 | 点击头像 → `uni.chooseImage` → 上传至 `/file/upload` → 落库 | `PUT /api/user/profile` |
| 改昵称 | 点击昵称 → 自研弹窗输入 → 落库 | `PUT /api/user/profile` |

* **身份安全**：接口只认当前登录会话，**无法修改他人资料**（`userId` 覆盖参数已彻底移除）
* **入参校验**：昵称 1~20 字符且不可全空白；头像地址必须为 `http/https`（拦截 `javascript:` 等协议注入）
* **内容合规**：昵称提交时接入微信官方 `msgSecCheck` 内容安全检测；微信接口不可用时降级放行并告警，不阻断正常用户
* **展示联动**：新昵称/头像即时生效于首页陪练卡片、订单详情、聊天室消息（`chat_message.sender_name` / `sender_avatar` 在发送时从数据库实时取）
* **立即生效机制**：选图后先用本地临时图**乐观更新**界面（无需等待上传完成）；
  上传成功后以服务端返回的档案覆盖本地状态并自增 `avatarVersion` 强制重建 `<image>` 组件，
  **规避微信小程序对同源图片 URL 的缓存**导致「数据库已更新但界面不刷新」的问题

> ⚠️ `uni.uploadFile` **不会**自动携带 `satoken`，所有上传必须走统一封装
> `miniapp/src/utils/upload.ts`（已内置 token header），否则会因未登录而静默失败。

### 4. 陪练师入驻：原声录音为「选填加分项」
* **不强制**：未录制原声同样可以提交入驻申请，**不阻断任何流程**
* **不造假**：未录制时 `companion_profile.voice_url` 保持 `NULL`、`voice_duration` 为 `0`，
  **绝不生成任何虚假音色兜底**（历史版本曾用假 mp3 冒充用户原声，已彻底下线）
* **降级展示**：首页陪练卡片对未录制者显示灰色「暂未录制原声」样式；管理员审核台显示「申请人未录制原声 (选填项)」
* **仍然必填**：真实姓名、手机号、至少 1 个游戏项目及其段位与段位截图（这是资质核验的底线，不可妥协）

### 5. 陪玩师准入控制（仅提交申请 ≠ 可接单）
> 修复前：只要**提交了**入驻申请（甚至被驳回），就可能进入接单工作台、开启接单开关、甚至抢单接单。
> 现已改为**后端硬门禁 + 前端真实状态分流**。

| 场景 | audit_status | 可进工作台 | 可开接单开关 | 可接单 |
| :--- | :--- | :--- | :--- | :--- |
| 未申请 | 无档案 | ❌ | ❌ | ❌ |
| **审核中** | 0 | ❌ 提示「审核中」 | ❌ | ❌ |
| **审核驳回** | 2 | ❌ 提示驳回原因 | ❌ | ❌ |
| **审核通过** | 1 | ✅ | ✅ | ✅ |
| 平台管理员 | — | ✅ | ✅ | ✅ |

* **后端硬门禁**：`getWorkbenchStats` / `toggleOnlineStatus` / `acceptOrder` 三处均强制校验 `audit_status = 1`，
  审核未通过一律抛出明确业务异常，**不依赖前端隐藏按钮**（前端可绕过，后端不可绕过）
* **状态查询接口**：`GET /api/companion/my-status` 返回 `{hasProfile, auditStatus, rejectReason, role, canAcceptOrder, isOnline}`
* **前端分流**：个人中心按真实审核状态展示四种入口（进入工作台 / 审核中 / 未通过可重新申请 / 去申请）；
  订单列表的「我收到的接单」陪玩师视角仅在审核通过后可见；全局接单守护（红点+震动）同样以审核状态为准

### 6. 订单状态与列表分类对照表
> 后端状态机 `companion_order.order_status` 共 9 种，列表分类与状态徽章**全覆盖**，不再出现「进行中」这种含糊兜底。

| 状态码 | 状态含义 | 徽章文案 | 所属分类 Tab | 徽章配色 |
| :--- | :--- | :--- | :--- | :--- |
| 0 | 待付款 | 待付款 | 全部 / 待付款（仅客户视角） | 灰色 |
| 1 | 已付款待接单 | 新预约待接单 / 已付款待接单 | 全部 / 待接单 | 黄色 |
| 2 | 陪玩师已接单(服务中) | 服务中 | 全部 / 服务中 | 靛蓝 |
| 3 | 陪玩师已申请完结 | 待确认完结 | 全部 / 待确认 | 粉色 |
| 4 | 订单已完成 | 订单已完成 | 全部 / 已完成 | 绿色 |
| 5 | 已取消 | 已取消 | 全部 / 已结束 | 浅灰 |
| 6 | 已退款 | 已退款 | 全部 / 已结束 | 红色 |
| 7 | 维权争议中 | 维权争议中 | 全部 / 已结束 | 橙色 |
| 8 | 仲裁退款完成 | 仲裁退款完成 | 全部 / 已结束 | 紫色 |

* **「已结束」分组**：后端以 `status IN (5,6,7,8)` 实现（`orderStatus = -2`），
  把「已取消 / 已退款 / 维权中 / 仲裁退款」归入同一终态分类，**不会只出现在「全部」里**
* **视角差异化 Tab**：客户视角 7 个 Tab（全部/待付款/待接单/服务中/待确认/已完成/已结束），
  陪玩师视角 6 个 Tab（无「待付款」——未支付成功的单子陪玩师根本不会收到）；Tab 条支持横向滚动
* **红点角标**：待接单数量**独立查询** `status=1`，不再用当前 Tab 的记录数推导
  （否则停留在「服务中」Tab 时红点会被错误清空）

### 7. 结算数据可见性（客户看不到平台内部账）
| 角色 | 可见内容 |
| :--- | :--- |
| 客户 | 仅订单金额与状态，**不展示任何清算/分佣/通道费信息** |
| 陪练师 | 仅「我的收益 + 清算单号 + 到账状态」，**不展示平台毛抽佣 / 通道成本 / 平台净利** |
| 平台管理员 | 完整清算对账单（毛抽佣、0.6% 通道费、平台净收益） |

### 8. 客户什么时候支付？（支付时机设计）

> **结论：客户在「确认预约」这一步完成支付（下单即支付），支付成功后订单才进入「已付款待接单(1)」，
> 陪玩师才会收到该订单。** 未支付的订单对陪练师完全不可见。

```text
客户点击「立即预约支付」
        │
        ├─ mock 模式 ────────► 后端自动模拟支付成功 ──► 订单 1(待接单) ──► 生成待通知事件
        │                        (前端直接跳转订单详情)
        │
        └─ 微信真实支付 ────► 返回 prepayId/timeStamp/nonceStr/package/signType/paySign
                                  │
                                  ▼
                        前端 uni.requestPayment 拉起微信支付面板
                                  │
                 ┌────────────────┴────────────────┐
                 │ 支付成功                          │ 支付失败/取消
                 ▼                                 ▼
        微信异步回调 /api/pay/notify        订单停留在 0(待付款)
        → 落盘 pay_flow                      → 详情页「立即支付」按钮可重试
        → 订单流转 1(待接单)
        → 生成待通知事件
```

| 环节 | 接口 | 说明 |
| :--- | :--- | :--- |
| 下单并支付 | `POST /api/order/create` | 返回 `PaymentResult`（含 `isMockPaid` 与微信五要素） |
| 继续支付 | `POST /api/order/pay/{orderSn}` | 对「待付款(0)」订单重新拉起支付，仅订单本人可发起 |
| 支付结果回调 | `POST /api/pay/notify` | 微信服务器异步通知（RSA 验签 + AES-GCM 解密），落盘支付事实并推进订单状态 |
| 手动触发（开发） | `POST /api/order/mock-pay/{orderSn}` | 仅开发环境可用 |

* **兜底**：客户取消/支付失败时订单停留在「待付款」，可在「我的订单 → 待付款」Tab 或订单详情页点击「立即支付」重试
* **付费才能看见**：陪练师侧只查询自己 `companion_user_id` 关联的订单，未支付成功的订单根本不会出现在其列表中

### 9. 微信通道费 0.6% 是怎么得出的？（重要澄清）

> **答：写死在配置里的「合同费率预估值」，不是从微信接口查出来的。**

| 问题 | 事实 |
| :--- | :--- |
| 微信是否提供「查询单笔实际通道费」的接口？ | **不提供**。微信支付 V3 的统一下单、支付回调、退款回调中都**不返回任何通道费字段** |
| 那 0.6% 从哪来？ | 商户与微信签约的生活服务 / 咨询类目**费率合同**（千分之六），由微信在**结算时从商户号可结算资金中实时截留** |
| 钱扣在哪一步？ | 玩家付款 50.00 元 → 商户号可结算资金实际到账 **49.70 元**，0.30 元在结算环节被扣走，**不体现在任何单笔接口响应里** |
| 系统里怎么记的？ | `pay_flow.channel_fee` 与 `order_settlement.channel_fee` 按 `total_amount × 费率` **预计算**落盘，用于实时估算平台净利润 |
| 怎么拿到真实值？ | 只能下载微信商户平台的**交易账单 / 对账文件**做月度对账。本系统的通道费是**估算值，不是结算凭证** |

**工程上的处理**：
* 费率集中在 `platform.channel-fee-rate`（默认 `0.006`），并支持**热配置**（管理员手机端「支付与配置 → 微信通道费率」），商户拿到实际合同费率后可直接改，**无需重启服务**
* 结算单中该字段的语义是「**按合同费率预估值**」，前端管理员页已明确标注此提示
* **对账纪律**：每月必须用微信交易账单核对 `pay_flow` 汇总值，差异即为费率调整或退款冲正造成的账面差

### 10. 消息通知机制（订阅消息已下线 → 平台线下通知）

> **背景**：微信小程序订阅消息是**一次性**机制 —— 用户每次授权仅获得 1 次接收配额。
> 陪玩师若未及时授权或配额耗尽，就会出现「老板已付款、陪玩师完全不知情」的漏单。
> 因此平台**不再依赖订阅消息**，改为**管理员线下兜底通知**。

**通知链路（三重保障）：**
```text
客户下单支付成功
      │
      ├─ ① 站内即时提示：订单列表「待接单」Tab 实时可见 + TabBar 红点角标 + 马达震动 + Toast
      │      (App.vue 全局 5 秒轮询守护, 陪练师停留在任意页面都能收到)
      │
      ├─ ② 平台线下通知：事件落库 notice_log → 管理员手机端工作台「待通知」Tab 实时可见
      │      (含当事人昵称 + 手机号, 管理员电话/微信/短信线下通知, 通知完点「已完成线下通知」)
      │
      └─ ③ 订单超时兜底：10 分钟未接单，系统自动原路全额退款并冲正支付流水
```

| 类型 | 触发时机 | 通知对象 |
| :--- | :--- | :--- |
| 新订单待接单 | 客户支付成功且已指定陪玩师 | 陪玩师 |
| 陪练师已接单 | 陪玩师接单成功 | 平台（掌握履约动态） |
| 入驻审核结果 | 管理员审核入驻申请 | 入驻申请人 |
| 售后维权 | 客户发起维权 | 平台（尽快介入协调） |

| 接口 | 说明 |
| :--- | :--- |
| `GET /api/admin/notice/pending` | 管理员查询待线下通知队列 |
| `POST /api/admin/notice/ack` | 标记某条事件已完成线下通知 |
| `GET /api/notice/test-send` | 生成一条测试用待通知事件 |

**已下线的微信订阅消息代码**：
* `miniapp/src/utils/subscribe.ts`（静默配额蓄水池）**已删除**
* `NoticeServiceImpl` 中的微信订阅消息下发逻辑**已移除**，改为事件落库
* `notice_log` 表结构已升级为「平台线下通知事件队列」（新增 `notice_type` / `title` / `user_name` / `phone` / `amount` / `finish_time`）

> ⚠️ 原「静默配额蓄水池」依赖用户已授权，属于**不可控的外发时机**；且微信对同一模板的一次性配额
> 会在用户未明确同意的情况下被拒收，无法保证送达。改为**平台人工兜底**是确定性更高的方案。

### 11. 游戏图标与游戏品类
* **图标托管在小程序包内** `/static/game/*.png`（`miniapp/src/static/game/`，共 24 张），
  **不再依赖任何第三方图床**：
  * 第三方图床会随时下线/改名（icons8 的「王者荣耀」`honor-of-kings.png` 已 404），
    且第三方域名在小程序正式版还必须加入 `downloadFile` 合法域名白名单，否则图片被直接拦截；
  * 本地静态资源随包发布，零网络依赖、零白名单配置。
* **图标样式**：圆角渐变底 + 中文游戏名（微软雅黑粗体），风格统一、绝不会张冠李戴
* **字典自愈**：`GameCatalogInitializer` 每次启动幂等校正 `game_category`（名称 + 图标 + 启用状态），缺失即补建
* **关联方式**：优先按 `companion_skill.game_id` **外键**关联（名称仅兜底），避免历史数据异常时取错图标
* **绝对禁止错标**：陪玩师申报的游戏若在字典中匹配不到，`game_id` 记为 `0`（未知游戏），
  **绝不回退到 id=1**，否则「英雄联盟 / 和平精英」会被静默标成「王者荣耀」，所有游戏显示同一个图标

**已支持 24 个主流品类**：

| 分类 | 游戏 |
| :--- | :--- |
| MOBA | 王者荣耀、英雄联盟、英雄联盟手游 |
| 战术射击 | 和平精英、无畏契约、CS2、穿越火线、暗区突围、三角洲行动 |
| 武侠/竞技 | 永劫无间、火箭联盟、守望先锋 |
| 休闲竞技 | 蛋仔派对、金铲铲之战、皇室战争、堡垒之夜、荒野乱斗、赛车竞速 |
| 其他 | 第五人格、原神、宝可梦大集结、火影忍者、DOTA2、星际征途 |

### 12. 管理员如何找到「客户已付款但陪玩师还没接」的订单

> 微信订阅消息为一次性机制已下线，陪玩师可能收不到提醒，**管理员必须能看到并主动联系陪玩师**。

管理员手机端工作台新增 **「📋 待接单」** 选项卡：

| 展示项 | 说明 |
| :--- | :--- |
| 订单号 / 游戏名 / 图标 | 快速定位是哪一单 |
| 订单金额 | 便于电话沟通 |
| **陪玩师昵称 + 手机号** | 三个按钮：**📞 拨打陪玩师**（微信原生拨号）/ 复制电话 / 复制订单号 |
| 客户需求备注 | 沟通时可直接照读 |
| 已等待分钟数 | ≥5 分钟标红，优先处理 |

排序规则：**最早下单的排最前**（先到先处理）。配合「📢 待通知」事件队列
（`GET /api/admin/notice/pending`）形成完整闭环：

```text
客户下单支付
   ├─▶ 订单状态 = 1(已付款待接单)
   ├─▶ 生成「新订单待接单」待通知事件（含陪玩师电话）
   └─▶ 管理员「📋 待接单」列表实时可见 → 一键拨打 → 线下催接单
          └─▶ 10 分钟未接单 → 系统自动为客户全额原路退款
```

| 接口 | 说明 |
| :--- | :--- |
| `GET /api/admin/order/pending-accept` | 待接单订单列表（含游戏图标 + 陪玩师联系方式） |
| `GET /api/admin/notice/pending` | 待线下通知事件队列 |
| `POST /api/admin/notice/ack` | 标记事件已完成线下通知 |

### 13. 订单倒计时（不重置）与履约超时自动退款

**问题**：早期倒计时是前端本地计数器，每次进入订单页 / 每次轮询都会被重置回 10:00，
用户看到的永远是「还剩 10 分钟」。

**修复**：倒计时改为**服务端按绝对截止时间计算**，随订单详情一起下发：

| 订单状态 | 阶段 | 截止时间 | 超时后果 |
| :--- | :--- | :--- | :--- |
| 1 已付款待接单 | 等待接单 | `create_time + 10 分钟` | 系统自动为客户**全额原路退款** |
| 2 服务中 | 履约服务 | `accept_time + 60 分钟` | 系统自动为客户**全额原路退款** |
| 3 待客户确认 | 等待确认 | `accept_time + 60 分钟` | 24 小时未确认由系统自动结单 |

* 字段：`order.remainingSeconds`（剩余秒数）+ `order.deadlineTime`（绝对截止时间）
* 前端只做本地递减展示，每次轮询再向服务端对齐，**任何时候进入页面都不会重置**
* 客户与陪玩师两端看到的是同一份服务端权威时间
* 新增配置：`platform.order-service-timeout-minutes`（默认 60）
* 新增调度任务：`OrderTimeoutScheduler.refundServiceTimeoutOrders()`，
  扫描「服务中」且接单超过 60 分钟的订单 → 退款 + 冲正 `pay_flow` + 聊天室系统提示

---

## 六、 微信小程序账号申请、主体类目与工信部备案保姆级指南

游戏开黑陪练类目在微信生态中属于强监管敏感地带。为避免被拒审或封号，请严格遵循以下合规标准：

### 1. 主体类型选择：必须是个体工商户或企业主体（千万不要用个人）
* **为什么不能用个人主体？** 个人主体**无法开通微信支付**，且无法选择生活服务/咨询类目；
* **初创推荐：个体工商户（成本最低、出照最快）**：各省政务服务网/App（浙里办、爱山东、粤商通等）免费在线办理，无需对公银行账户，法人个人实名微信扫脸认证即可；
* **经营范围技巧**：建议包含 `信息技术咨询服务`、`数字文化创意软件开发`、`体育健康服务`、`商务代理代办服务`；⚠️ **严禁出现**：`游戏代练`、`代打`、`租号`、`虚拟货币交易`。

### 2. 小程序命名与简介避坑（第一道红线）
* ❌ **严禁出现的敏感词**：`陪玩`、`约玩`、`代练`、`代打`、`上分`、`带飞`、`美女陪聊`；
* ✅ **高通过率名称范例**：`XX开黑搭子`、`XX电竞组队助手`、`XX电竞技能指导`；
* **简介范例**：*“提供游戏玩家组队开黑、电竞玩法技巧交流与战术指导的在线工具助手。”*

### 3. 核心服务类目选择（免除特许行业资质的死穴）
* ❌ **千万不要碰的类目**：`文娱 - 社交/论坛`（需要极难办理的增值电信业务许可证 ICP/EDI）、`游戏 - 游戏服务平台`（需要网络文化经营许可证）；
* ✅ **免许可证黄金类目**：`生活服务 - 商务服务` 或 `生活服务 - 咨询服务`；仅需上传**营业执照彩色拍照 + 法人身份证**，通常 1 个工作日过审。

### 4. 工信部小程序备案要点
1. 负责人手机号必须是运营商实名在法人名下的号码；
2. 应急联系人手机号绝对不能与负责人相同；
3. **工信部 12381 短信核验**：微信初审通过后，工信部会下发带有 6 位验证码的短信，**必须在 24 小时内**登录工信部官方链接（`beian.miit.gov.cn`）完成核验，超时会被系统直接驳回作废。

---

## 七、 消息通知链路配置与上线检查

> 微信小程序订阅消息（一次性配额）已于 v1.1 阶段**全线下线**，详见第四章「消息通知机制」。
> 平台改为「**站内即时提示 + 管理员线下通知兜底 + 订单超时自动退款**」三重链路，**上线无需申请任何订阅消息模板**。

### 1. 链路组成

| 层级 | 实现 | 需要配置 |
| :--- | :--- | :--- |
| ① 站内即时提示 | `App.vue` 全局轮询 → 「待接单」Tab + TabBar 红点角标 + 马达震动 + Toast | 无 |
| ② 平台线下通知 | 事件落库 `notice_log`，管理员工作台「📢 待通知」/「📋 待接单」Tab 展示昵称 + 手机号 | 无 |
| ③ 资金兜底 | 10 分钟未接单，`OrderTimeoutScheduler` 自动原路全额退款并冲正 `pay_flow` | `platform.order-accept-timeout-minutes`（默认 10） |

### 2. 上线前检查

1. 客户下单支付成功 → 陪玩师端 TabBar 出现红点，进入「待接单」Tab 能看到该订单；
2. 管理员工作台「📢 待通知」出现「新订单待接单」事件（含陪玩师昵称与手机号）；
3. 点击「📞 拨打陪玩师」可正常唤起微信拨号；
4. 把订单 `create_time` 调整为 11 分钟前，等待调度扫描（默认 60 秒）→ 订单应自动流转「已退款(6)」，`pay_flow` 同步冲正；
5. 全程不依赖任何微信订阅消息模板。

> 注：管理员「支付与配置」中残留的「新订单模板ID」输入项已无下发逻辑，填写与否均不影响通知链路。

---

## 八、 自动化单元测试验证报告

全部业务逻辑在本地通过 Maven 和 JDK 17 完成了端到端全链路自动化单元测试：
* **测试用例类**：`server/src/test/java/com/game/companion/OrderFlowIntegrationTest.java`
* **执行命令**：`mvn test -Dtest=OrderFlowIntegrationTest`
* **测试结论**：**`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS`**

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S  R E S U L T S
[INFO] -------------------------------------------------------
✅ test1_UserLoginAndRoleSeparation       : 双端身份隔离与UnionId空串安全防御 (通过)
✅ test2_SelfOrderMustBeForbidden          : 陪玩师严禁预约自己拦截 (通过)
✅ test3_ClientOrderAndCompanionReceiveCheck: 业务订单生成 -> pay_flow 支付流水事实记录与0.6%通道费校验 (通过)
✅ test4_CompanionAcceptAndCompleteFlow    : 履约完结 -> 钱包入账 -> order_settlement 清算单生成 (净利严格核算) -> 好评与评分联动 (通过)
✅ test5_ClientCancelAndRefundOrder        : 客户待接单主动撤单 -> pay_flow 支付流水退款冲正 (通过)
✅ test6_RealChatSendAndReceive            : 开黑房间双方真实即时双向聊天 (通过)
✅ test7_CompanionMultiGameSkillsCheck     : 陪练师多项目与差异化计价 (通过)
✅ test8_AddOnOrderExtensionAndDisputeFlow : 加钟解耦保护 -> 售后维权申请 -> 管理员仲裁全额退款 (通过)
✅ test9_TimeoutAutoRefundTest             : 10分钟接单超时自动退款与流水冲正 (通过)
✅ test10_WithdrawApplyAndAdminAuditFlow   : 钱包乐观锁并发版本控制 -> 提现与打款审批 (通过)
✅ test11_PlatformDynamicConfigHotUpdateTest: 管理员后台热配置保存与秒级生效 (通过)
✅ test12_OrderStateMachineAndAuthorizationGuard: 状态机跳步拦截 + 越权接单/越权结单/重复清算拦截 (通过)
✅ test13_CommissionHotConfigActuallyAffectsSettlement: 抽佣比例热配置真实影响清算金额 (通过)
✅ test14_UpdateProfileAndOptionalVoice: 自助改头像昵称 + 陪练入驻录音选填 + 自定义昵称不被覆盖 (通过)
✅ test15_CompanionAdmissionAndOrderStatusGrouping: 陪玩师准入门禁 + 订单状态分类分组筛选 (通过)
✅ test16_GameCatalogAndIconResolution: 游戏字典自愈 + 图标/gameId 关联 + 管理员待接单列表带图标与电话 (通过)
✅ test17_CountdownNotResetAndServiceTimeoutRefund: 倒计时按绝对截止时间不重置 + 履约超时自动全额退款 (通过)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 九、 平台安全与鉴权模型（默认拒绝）

> 本章为安全加固后的强制规范。**任何新增接口都必须默认处于「需要登录」状态**。

### 1. 全局登录态拦截（`AuthInterceptorConfig`）
采用「白名单放行 + 其余全部要求登录」的**默认拒绝**策略，避免新增接口时因漏加注解造成越权裸奔。

* **白名单（可匿名访问）**：
  * `/user/wx-login` —— 微信静默登录
  * `/game/**` —— 游戏品类字典
  * `/companion/list`、`/companion/skills` —— 陪练师公开浏览与技能定价
  * `/file/download/**` —— 多媒体流式直读（真机试听原声、战绩截图预览）
  * `/pay/notify` —— 微信支付异步回调（请求体经微信官方 RSA 验签 + AES-GCM 解密校验）
  * 接口文档与错误页
* **未登录响应**：HTTP 200 + 业务码 `401`，与项目统一响应约定一致，前端 `utils/request.ts` 已内置「401 → 静默重登 → 原地重试」自愈机制。

### 2. 身份一律取自登录会话（`AuthGuard`）
**严禁**通过请求参数伪造身份。以下参数已全部从后端接口中移除，客户端传入一律被忽略：

| 历史危险参数 | 风险 | 现状 |
| :--- | :--- | :--- |
| `/order/confirm?clientUserId=他人ID` | 冒充客户确认结单，直接给他人钱包加钱 | ❌ 已移除，取会话身份 |
| `/order/accept?companionUserId=他人ID` | 冒充陪玩师接单 | ❌ 已移除，取会话身份 |
| `/order/dispute/audit?adminUserId=他人ID` | 冒充管理员仲裁退款 | ❌ 已移除，强制管理员角色 |
| `/chat/send` 的 `senderId` | 冒名发言、注入他人会话 | ❌ 已移除，取会话身份 |
| `/wallet/withdraw/apply?userId=他人ID` | 冒名提现 | ❌ 已移除，取会话身份 |
| `/user/bind-phone?userId=他人ID` | 把手机号绑到他人账号 | ❌ 已移除，取会话身份 |
| `/user/info?userId=他人ID` | 遍历用户档案 | ❌ 已移除，取会话身份 |

### 3. 角色级权限（`AuthGuard.requireAdmin`）
* `/admin/**`、`/wallet/admin/**`、`/order/dispute/audit/**` —— 强制 `role = 9`
* `/companion/toggle-online` —— 强制 `role = 2`（管理员放行）

### 4. 资源级越权防护
* **订单详情**：仅下单客户 / 接单陪玩师 / 原指定陪玩师 / 管理员可查（避免泄露游戏账号与区服）
* **聊天室**：订单房间仅客户与陪玩师可读写；咨询房间（`CONSULT_{min}_{max}`）仅双方可读写
* **评价**：仅下单客户本人可提交

### 5. 开发期特权工具生产禁用
`/user/mock-switch-role`、`/order/mock-pay/{sn}`、`/user/debug/all`、`/order/debug/list` 由 `platform.dev-tools-enabled` 开关控制：
* 生产环境**必须为 `false`**（`application.yml` 默认即 `false`，仅 `dev` profile 开启）
* 即使开启，也**只允许切换调用者本人**的角色，MUST NOT 切换他人

---

## 十、 自动化运维：订单超时调度

> 早期版本中「超时自动退款」仅暴露为需外部手动触发的 HTTP 接口，线上并不会真正自动执行。
> 现已补齐为真正的后台调度任务（`OrderTimeoutScheduler`）。

| 调度任务 | 扫描条件 | 动作 | 配置项 |
| :--- | :--- | :--- | :--- |
| 接单超时自动退款 | 状态 = 1（待接单）且 `create_time` 早于「当前时间 − 超时阈值」 | 原路全额退款，订单 → 6，支付流水冲正 | `platform.order-accept-timeout-minutes`（默认 10） |
| 完结超时自动结单 | 状态 = 3（已申请完结）且 `update_time` 早于「当前时间 − 超时阈值」 | 自动生成清算单并划入陪玩师钱包 | `platform.order-finish-timeout-hours`（默认 24） |

* 扫描间隔：`platform.scheduler.scan-interval-ms`（默认 60 秒），单次最多处理 200 条
* **超时判定是时间感知的**：刚下单的订单不会被误退款（单元测试 9 已覆盖该断言）

---

## 十一、 敏感配置与密钥治理

### 1. 版本库中不再保留任何真实密钥
`application.yml` 中所有微信 / 支付参数一律改为**环境变量占位符**：

```yaml
wx:
  miniapp:
    appid: ${WX_MINIAPP_APPID:}
    secret: ${WX_MINIAPP_SECRET:}
  pay:
    mchId: ${WX_PAY_MCH_ID:}
    apiV3Key: ${WX_PAY_API_V3_KEY:}
```

### 2. 本地私有配置（不进版本库）
* 复制 `server/src/main/resources/application-local.yml.example` → `application-local.yml` 填写本地真实值
* `application-local.yml` 已加入 `.gitignore`（同时忽略了 `uploads/` 运行时上传目录）
* 通过 `spring.profiles.include: local` 自动加载，文件不存在时自动跳过

### 3. 环境变量注入方式（PowerShell）
```powershell
$env:WX_MINIAPP_APPID    = "wx...."
$env:WX_MINIAPP_SECRET  = "...."
$env:WX_PAY_MCH_ID      = "...."
$env:WX_PAY_API_V3_KEY  = "...."
$env:SPRING_PROFILES_ACTIVE = "prod"
```

### 4. 微信支付 V3 真实可用性
`WechatV3PaymentServiceImpl` 已实现 **JSAPI 下单 / 原路退款 / 商家转账到零钱** 三项生产能力，并新增 `/pay/notify` 异步回调网关。
与早期「静默返回成功」的空壳实现不同：**商户参数缺失、验签失败、退款被拒一律抛出明确异常，绝不伪造成功**。

| 能力 | 实现 | 失败处理 |
| :--- | :--- | :--- |
| JSAPI 统一下单 | `createOrderV3` + RSA 签名，输出前端 `wx.requestPayment` 五要素 | 抛异常，订单停留「待付款」 |
| 原路退款 | `refundV3`，退款额与原单额一并上送 | 微信非 SUCCESS/PROCESSING 即抛异常，**订单不会标记为已退款** |
| 商家转账到零钱 | `MerchantTransferService.createTransfer`，返回微信批次单号 | 抛异常，冻结资金保持冻结待重试 |
| 支付异步回调 | 官方 RSA 验签 + AES-GCM 解密 | 验签失败直接拒绝，**绝不落盘支付事实** |

---

## 十二、 上线前必做清单（Deployment Checklist）

> 本章是**上线前的强制动作项**，逐条完成并勾选后方可对外运营。
> 涉及资金与账号安全，**任何一条遗漏都可能导致资损、漏单或封号**。

### ☐ 1. 密钥轮换（最高优先级，必做）

> ⚠️ 仓库历史提交 `5da4019`（`feat: initial commit`）中曾出现过**明文 AppID / AppSecret / 订阅消息模板 ID**。
> 虽然当前代码已全部改为环境变量占位符，但**密钥本身已经泄漏在 Git 历史中**，
> 只要不重置，就等同于把小程序后台的钥匙挂在公网上。

**操作步骤：**

1. 登录 [微信公众平台](https://mp.weixin.qq.com/) → **开发管理 → 开发设置 → AppSecret**
2. 点击 **「重置」**，系统会生成一个全新的 AppSecret（旧 Secret 立即失效）
3. 同步更新本地私有配置（该文件已被 `.gitignore` 忽略，不会进入版本库）：
   ```yaml
   # server/src/main/resources/application-local.yml
   wx:
     miniapp:
       appid: "wx........"
       secret: "【重置后的新 Secret】"
   ```
   或注入环境变量：
   ```powershell
   $env:WX_MINIAPP_SECRET = "【重置后的新 Secret】"
   ```
4. 重启后端，确认日志出现 `✅ [WxMaService] 微信官方小程序服务引擎已成功加载！` 且**不再有** `微信官方 API 换取 session 失败` 的报错
5. 真机重新走一遍「静默登录 → 绑定手机号 → 下单 → 收到派单通知」全链路，确认 OpenID 正常下发

**顺带建议（可选但推荐）：**
* 若该仓库是公开仓库或曾推送到第三方 Git 服务，建议在 GitHub 仓库设置中启用 **「推送保护 / Push Protection」**，并在仓库中提示后来者**不要使用历史密钥**
* 若有条件，可对历史提交做一次 `git filter-repo` 清理（**注意：会重写提交历史，需团队协商后执行**）

### ☐ 2. 配置微信支付异步回调地址 `/pay/notify`

> 真实支付模式下，微信支付成功后会向该地址推送**加密的支付结果通知**。
> **未配置该地址 = 真实模式下永远不会产生 `pay_flow` 支付事实流水**，
> 表现为「用户已付款，但订单一直卡在待付款、陪练收不到派单通知」。

**配置方式（二选一，推荐方式 A）：**

| 方式 | 操作 | 说明 |
| :--- | :--- | :--- |
| **A. 环境变量**（推荐） | `WX_PAY_NOTIFY_URL=https://你的域名/api/pay/notify` | 部署期固定，不受数据库误操作影响 |
| B. 管理员热配置中心 | 小程序「我的 → 连击 5 次管理员暗门 → 支付与配置」填入 | 运营期可改，误操作会立即生效 |

**完整上线步骤：**

1. 准备一个**已备案、已配置 HTTPS** 的正式域名（例如 `https://api.你的域名.com`）
2. 微信公众平台 → **开发管理 → 开发设置 → 服务器域名 → request 合法域名**，加入你的域名
3. 设置环境变量：
   ```powershell
   $env:WX_PAY_NOTIFY_URL = "https://api.你的域名.com/api/pay/notify"
   ```
4. 微信支付商户平台（[pay.weixin.qq.com](https://pay.weixin.qq.com/)）→ **产品中心 → JSAPI 支付 → 支付配置 → 支付回调地址**，填入同一地址
   > ⚠️ 此处与 `WX_PAY_NOTIFY_URL` **必须是完全一致的地址**，否则微信回调会被拒
5. 同步补齐其余商户参数：
   ```yaml
   # application-local.yml 或环境变量
   wx:
     pay:
       mchId: "商户号"
       apiV3Key: "APIv3 密钥（32位）"
       keyPath: "classpath:cert/apiclient_key.pem"   # 或改用热配置中心填入私钥 PEM 文本
       certPath: "classpath:cert/apiclient_cert.pem"
       notify-url: "https://api.你的域名.com/api/pay/notify"
       transfer-scene-id: "【商家转账到零钱】经营场景ID"
   ```
6. 补齐**商户私钥与证书**：`server/src/main/resources/cert/` 目录下放入
   `apiclient_key.pem`（商户 API 私钥）、`apiclient_cert.pem`（商户证书），
   并在商户平台下载对应的**证书序列号**填入热配置中心
7. 在小程序「管理员暗门 → 支付与配置」中把 **支付模式** 从 `mock` 切换为 **`wechat`**
8. **端到端验证（必做）**：
   * 下单 → 前端应拉起真实 `wx.requestPayment` 支付面板
   * 支付完成后查库：`pay_flow` 应新增一条 `pay_status = 1` 的记录，`channel_fee` 为 0.6% 通道费
   * `companion_order.order_status` 应流转为 **1（已付款待接单）**
   * 管理员工作台「📢 待通知」应出现该订单的「新订单待接单」事件

### ☐ 3. 验证通知链路（无需订阅消息模板）

微信订阅消息已全线下线，**不需要也不应再申请**任何模板 ID，通知链路为「站内提示 + 管理员线下通知 + 超时退款」：

1. 客户下单支付 → 陪玩师端 TabBar 红点，且「待接单」Tab 实时可见该订单；
2. 管理员工作台「📢 待通知」出现「新订单待接单」事件（含陪玩师昵称、手机号），可一键拨号；
3. 下单后 10 分钟无人接单 → 订单自动流转「已退款(6)」且 `pay_flow` 冲正；
4. 详细链路说明见 [第七章](#七-消息通知链路配置与上线检查)。

### ☐ 4. 关闭开发期特权开关

生产环境**必须**确保：

```yaml
# application.yml（默认值即为 false，dev profile 会覆盖为 true）
platform:
  dev-tools-enabled: false
```

* 确认生产 profile **不是** `dev`
* 验证：`/user/mock-switch-role`、`/order/mock-pay/{sn}`、`/user/debug/all`、`/order/debug/list` 全部返回 403

### ☐ 5. 其余上线动作（沿用既有指引）

* 申请个体工商户营业执照（经营范围含「信息技术咨询服务」，**严禁出现代练/代打**）
* 微信小程序选择 `生活服务 - 商务服务` 类目，完成工信部 12381 短信核验
* 开通用户隐私保护指引（麦克风 / 录音信息）
* 开通微信支付商户号 + 「商家转账到零钱」权限
* 设置 `platform.commission-rate`（建议 **3%~5%**，切勿 0% 免佣，微信 0.6% 通道费会倒贴）
* 首次上线前执行一次 `mvn test -Dtest=OrderFlowIntegrationTest` 确认核心链路无回归

---

## 十三、React Web 前端（`web/`）

### 1. 技术栈与工程位置

Web 端是独立于 `server/`、`miniapp/` 的第三个工程，目录为 `web/`：

* **Vite 5 + React 18 + TypeScript**
* **React Router 6**：路由与角色守卫
* **TanStack Query 5**：服务端状态、轮询与缓存失效
* **Tailwind CSS + shadcn/ui 风格组件**（Radix 原语）
* **React Hook Form + zod**：入驻申请与配置表单校验
* **Vitest + Testing Library**：金额、状态映射、时间与 API 客户端单测

### 2. 本地启动

```bash
cd web
npm install
npm run dev
```

* 访问地址：`http://localhost:5174`
* `/api` 由 Vite 代理到 `http://localhost:8088`，与小程序、后端端口约定一致
* 可通过 `VITE_API_PROXY_TARGET` 覆盖代理目标，通过 `VITE_API_BASE_URL` 覆盖前端 API 基地址

### 3. 登录方式

**生产/公网**：手机号 + 密码注册、登录。管理员使用同一入口，角色由后端 `sys_user.role` 决定，登录后自动进入管理后台；不再使用 mock 身份。

首次部署可通过环境变量创建管理员：

```bash
BOOTSTRAP_ADMIN_PHONE=13800000000
BOOTSTRAP_ADMIN_PASSWORD=至少8位密码
BOOTSTRAP_ADMIN_NICKNAME=平台管理员
```

创建成功后请从部署环境中移除这三个变量。生产配置必须保持：

```yaml
auth:
  mock-login-enabled: false
platform:
  dev-tools-enabled: false
```

**本地开发**：登录页保留客户/陪练/管理员三个开发快捷入口，仅在 `import.meta.env.DEV` 下显示；需要：

```yaml
auth:
  mock-login-enabled: true
platform:
  dev-tools-enabled: true
```

`application-dev.yml` 已默认开启。生产构建会隐藏开发快捷入口，后端也会拒绝 `mock_` 前缀登录。

> 当前版本为手机号 + 密码，已接入 Redis 登录失败限流（同一手机号 15 分钟失败 5 次、同一 IP 失败 30 次会暂时锁定）。短信验证码、找回密码尚未接入，公网运营前建议补充短信服务商。

### 4. 已覆盖页面

* **客户**：精选陪练、游戏筛选、陪练详情/技能/原声/评价、确认下单、订单列表与详情、待接单倒计时、撤回/确认/维权、订单内聊天、评价、个人资料、钱包流水
* **陪练**：多游戏入驻申请（截图 + 原声选填）、入驻状态四态分流、工作台（上下线、待接单、服务中）、接单、申请完结、收益与提现
* **管理员**：经营大盘、入驻审核（凭证大图/音频）、提现审核、待接单队列、待通知队列、平台热配置（密钥不回显）

### 5. Docker 部署（推荐）

服务器只需安装 Docker 24+ 与 Docker Compose v2，镜像内已包含 JDK、Nginx、Node 构建环境。

#### 5.1 目录与文件

```text
game-companion/
├── docker-compose.yml            # MySQL + Redis + 后端 + Web
├── docker-compose.https.yml      # HTTPS 覆盖配置（容器内终止 TLS）
├── .env.docker.example           # 环境变量模板
├── docker/ssl/                   # 域名 HTTPS 证书（不提交）
├── docker/certs/                 # 微信支付商户证书（不提交）
├── server/Dockerfile
├── web/Dockerfile
└── web/nginx.ssl.conf            # HTTPS 站点配置
```

架构：

```text
公网 443
  │
  ▼
web 容器 :443（Nginx：TLS 终止 + 静态资源 + /api 反向代理）
  │   :80 仅用于 301 跳转和容器健康检查
  ▼
server 容器 :8088（Spring Boot）
  ├── mysql 容器 :3306
  └── redis 容器 :6379
```

后端、MySQL、Redis 都只在 Compose 内部网络暴露，不对公网开放。

#### 5.2 准备证书与环境变量

```bash
cd /opt/game-companion

# 1. 环境变量
cp .env.docker.example .env
chmod 600 .env
vi .env

# 2. HTTPS 域名证书（fullchain 必须包含中间证书）
sudo mkdir -p docker/ssl
sudo cp /path/to/fullchain.pem docker/ssl/fullchain.pem
sudo cp /path/to/privkey.pem   docker/ssl/privkey.pem
sudo chmod 600 docker/ssl/*.pem
```

`.env` 至少需要修改：

```bash
MYSQL_ROOT_PASSWORD=强密码
MYSQL_PASSWORD=强密码
REDIS_PASSWORD=强密码

WEB_BASE_URL=https://你的域名
CORS_ALLOWED_ORIGINS=https://你的域名

PLATFORM_PAYMENT_MODE=wechat
WX_PAY_NOTIFY_URL=https://你的域名/api/pay/notify
WX_PAY_WEB_APP_URL=https://你的域名
```

证书域名必须与上述三个域名完全一致，否则浏览器、微信支付回调都会出问题。

#### 5.3 首次部署（HTTPS）

```bash
cd /opt/game-companion

# 建议先校验 Compose 配置
docker compose -f docker-compose.yml -f docker-compose.https.yml config > /dev/null

# 构建并启动
docker compose -f docker-compose.yml -f docker-compose.https.yml up -d --build

# 查看状态
docker compose -f docker-compose.yml -f docker-compose.https.yml ps

# 健康检查
curl -s https://你的域名/api/health
```

应返回：

```json
{"code":200,"data":{"name":"PP电竞鼠 (PP Esports)"}}
```

为减少重复参数，建议在当前 shell 定义：

```bash
DC="docker compose -f docker-compose.yml -f docker-compose.https.yml"
$DC ps
$DC logs -f server
```

下文命令默认使用 `$DC`；新开终端需要重新定义，否则 `docker compose up` 会以纯 HTTP 配置重建 Web 容器。

首次启动时 MySQL 数据卷为空，会自动执行 `server/src/main/resources/init_schema.sql` 建库建表并写入游戏品类数据。

> `init_schema.sql` 含 `DROP TABLE`，只会在数据卷为空时执行一次，后续重启不会重复执行。

服务器安全组/防火墙需要放行 **80** 与 **443**。

#### 5.4 纯 HTTP 部署（不推荐）

没有证书或仅在局域网验证时：

```bash
docker compose up -d --build
curl http://127.0.0.1/api/health
```

限制：

- 微信 H5 支付必须使用 HTTPS，纯 HTTP 只能用于本地或内网联调
- 80 端口被占用时修改 `.env` 的 `WEB_HTTP_PORT`
- 后续要切 HTTPS，直接按 5.2、5.3 加证书并用覆盖配置重建即可
- 用 `http://IP:端口` 访问时，把该 Origin 一并加入白名单，避免 POST 被 CORS 拦截：

  ```bash
  CORS_ALLOWED_ORIGINS=http://8.137.89.240:8080,https://你的域名
  ```

  新版 web 镜像已按 `Host` 端口正确转发 `X-Forwarded-Port`，同源请求不会触发 CORS；旧镜像可通过白名单兜底。

如果 HTTPS 必须由**已有宿主机 Nginx** 终止（例如该 Nginx 还服务其它站点），则不要用 `docker-compose.https.yml`：

```bash
# .env: WEB_HTTP_PORT=8080（避免与宿主机 80 冲突）
docker compose up -d --build
```

宿主机 Nginx 关键配置：

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Port 443;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    # SSE 必须关闭缓冲
    proxy_buffering off;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}
```

#### 5.5 已有数据库升级

不要执行 `init_schema.sql`，改为执行增量迁移：

```bash
# 先备份
$DC exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction game_companion' \
  > backup_$(date +%F).sql

# 执行迁移（脚本可重复执行，遇到重复数据会中止并提示）
$DC exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" game_companion' \
  < server/src/main/resources/db/migration_web_launch.sql
```

#### 5.6 微信支付证书

```bash
cp apiclient_key.pem apiclient_cert.pem docker/certs/

# 后端容器以 UID 10001 运行，必须让该用户可读
sudo chown 10001:10001 docker/certs/*.pem
chmod 600 docker/certs/*.pem

$DC restart server
```

`docker/certs` 挂载到容器内 `/opt/certs`，与 `.env` 默认路径一致。证书已加入 `.gitignore`。

若日志出现 `Permission denied`，先检查宿主机证书属主是否为 `10001:10001`。

#### 5.7 常用运维命令

```bash
# 日志
$DC logs -f server
$DC logs -f web

# 更新代码后重建
git pull
$DC up -d --build

# 只重启后端
$DC restart server

# 停止（保留数据）
$DC down

# 停止并删除所有数据卷（危险：会清空数据库、Redis、上传文件）
$DC down -v

# 进入数据库
$DC exec mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" game_companion'

# 检查 Nginx 配置
$DC exec web nginx -t
```

数据卷：

| 卷名 | 内容 |
| :--- | :--- |
| `game-companion_mysql-data` | MySQL 数据 |
| `game-companion_redis-data` | Redis AOF |
| `game-companion_uploads` | 用户上传的头像、截图、音频 |

`uploads` 对应后端 `/app/uploads`，删除后已上传文件将无法访问。

#### 5.8 证书续期

续期后替换文件并重载 Nginx，无需重启后端：

```bash
sudo cp /path/to/new/fullchain.pem docker/ssl/fullchain.pem
sudo cp /path/to/new/privkey.pem   docker/ssl/privkey.pem
sudo chmod 600 docker/ssl/*.pem
$DC exec web nginx -t && $DC exec web nginx -s reload
```

Let's Encrypt 证书可用 `certbot renew` 配合 deploy hook 自动复制并 reload：

```bash
# /etc/letsencrypt/renewal-hooks/deploy/game-companion.sh
#!/usr/bin/env bash
cp /etc/letsencrypt/live/你的域名/fullchain.pem /opt/game-companion/docker/ssl/fullchain.pem
cp /etc/letsencrypt/live/你的域名/privkey.pem   /opt/game-companion/docker/ssl/privkey.pem
chmod 600 /opt/game-companion/docker/ssl/*.pem
docker exec gc-web nginx -s reload
```

#### 5.9 Docker 部署注意事项

- 生产环境务必修改 `.env` 中所有默认密码，`.env` 不要提交
- `PLATFORM_PAYMENT_MODE=wechat` 时未配置商户参数会导致支付报错，这是预期行为，不会假成功
- `BOOTSTRAP_ADMIN_*` 只在首次启动时创建管理员，创建成功后请删除并重启
- 镜像构建需要访问 Docker Hub、Maven Central、npm Registry，国内服务器建议配置镜像加速
- 服务器建议 2 核 4G 以上，首次构建 JVM 与 Node 比较吃内存
- MySQL 首次初始化需要几十秒，请等待健康检查通过再访问
- 上传接口返回的 URL 依赖 `X-Forwarded-*`，本方案已在 Nginx 与后端配置好，换成其它代理时需保留这些请求头

#### 5.10 镜像拉取超时与构建加速

如果 `docker compose up` 报：

```text
failed to resolve reference "docker.io/library/redis:7-alpine": dial tcp ...: i/o timeout
```

说明服务器无法直连 Docker Hub，需要配置镜像加速或代理。

**方案 1：Docker daemon 镜像加速（推荐）**

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json >/dev/null <<'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.m.daocloud.io",
    "https://dockerproxy.net"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker

# 验证是否生效
sudo docker pull redis:7-alpine
```

说明：

- `registry-mirrors` 只对 Docker Hub 镜像生效，本项目用到的 MySQL、Redis、Nginx、Node、Maven、Temurin 都走它
- 公共镜像站可用性会变化，上面的地址失效时换一个再试，或使用自己搭建的 mirror
- 配置后镜像拉取、`docker build` 的基础镜像下载都会变快

**方案 2：给 Docker 配置 HTTP 代理**

如果服务器能访问某个代理：

```bash
sudo mkdir -p /etc/systemd/system/docker.service.d
sudo tee /etc/systemd/system/docker.service.d/http-proxy.conf >/dev/null <<'EOF'
[Service]
Environment="HTTP_PROXY=http://代理地址:端口"
Environment="HTTPS_PROXY=http://代理地址:端口"
Environment="NO_PROXY=localhost,127.0.0.1,::1"
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
```

代理必须放行 `registry-1.docker.io`、`auth.docker.io`、`production.cloudflare.docker.com`。

**方案 3：Maven / npm 构建加速**

Docker Hub 通了之后，镜像构建仍可能卡在依赖下载。在 `.env` 中加入：

```bash
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
NPM_REGISTRY=https://registry.npmmirror.com
```

然后重新构建：

```bash
docker compose -f docker-compose.yml -f docker-compose.https.yml build --no-cache
docker compose -f docker-compose.yml -f docker-compose.https.yml up -d
```

**方案 4：离线导入镜像**

在能联网的机器上构建并导出：

```bash
docker compose build
docker save game-companion/server:latest game-companion/web:latest | gzip > gc-images.tar.gz
# 同时导出基础镜像：mysql:8.0 redis:7-alpine

scp gc-images.tar.gz user@服务器:/tmp/
```

服务器导入：

```bash
docker load < /tmp/gc-images.tar.gz
docker compose -f docker-compose.yml -f docker-compose.https.yml up -d
```

排查顺序建议：先确认 `sudo docker pull redis:7-alpine` 能成功，再执行 `up -d --build`。

### 6. 构建与本地验收

```bash
# 本地/CI 先跑测试；生产打包跳过测试，避免测试库操作误伤数据
cd server
mvn test
mvn clean package -DskipTests

cd ../web
npm ci
npm run typecheck
npm test
npm run build
```

后端产物：`server/target/game-companion-server-1.0.0-SNAPSHOT.jar`

前端产物：`web/dist/`

### 7. 生产部署架构

推荐同源部署：

```text
浏览器
  │ HTTPS 443
  ▼
Nginx
  ├── /            → /var/www/pp-esports (web/dist)
  └── /api/*       → 127.0.0.1:8088 (Spring Boot)
                         ├── MySQL 8
                         └── Redis 7
```

要求：

- Linux 服务器，JDK 17、Maven 3.9+、Node.js 20+、MySQL 8、Redis 7、Nginx
- 域名已备案，HTTPS 证书有效
- 后端 8088 只监听内网/本机，不对公网开放
- MySQL、Redis 只允许内网访问
- 微信支付回调地址必须是公网 HTTPS

### 8. 后端部署

#### 8.1 数据库

首次全新部署：

```bash
mysql -uroot -p -e "CREATE DATABASE game_companion DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p game_companion < server/src/main/resources/init_schema.sql
```

> `init_schema.sql` 会先 `DROP TABLE`，只能用于全新库，禁止直接在生产存量库执行。

已有数据库升级：

```bash
# 先备份
mysqldump -uroot -p --single-transaction game_companion > game_companion_backup_$(date +%F).sql

# 使用 mysql CLI 执行，脚本包含 DELIMITER 过程定义
mysql -uroot -p game_companion < server/src/main/resources/db/migration_web_launch.sql
```

迁移脚本会补齐：

- `sys_user.password_hash` 与手机号唯一键
- 提现人工打款字段
- `pay_flow.order_sn` 唯一键
- `chat_message.message_type`
- `chat_read_state` 已读表
- `chat_room` 聊天室摘要表及历史会话回填
- `companion_skill`、`game_category` 唯一键

若库中已存在重复手机号、重复支付单号、重复技能或重复游戏名，脚本会中止并提示，需先人工合并，不会静默删除数据。

#### 8.2 环境变量

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8088

SPRING_DATASOURCE_URL=jdbc:mysql://内网MySQL:3306/game_companion?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=game_companion
SPRING_DATASOURCE_PASSWORD=强密码

SPRING_DATA_REDIS_HOST=内网Redis
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=Redis密码
SPRING_DATA_REDIS_DATABASE=0

WEB_BASE_URL=https://web.你的域名
CORS_ALLOWED_ORIGINS=https://web.你的域名

PLATFORM_DEV_TOOLS_ENABLED=false
AUTH_MOCK_LOGIN_ENABLED=false
PLATFORM_PAYMENT_MODE=wechat
KNIFE4J_ENABLE=false

# 首次创建管理员，创建成功后从环境变量移除
BOOTSTRAP_ADMIN_PHONE=13800000000
BOOTSTRAP_ADMIN_PASSWORD=至少8位强密码
BOOTSTRAP_ADMIN_NICKNAME=平台管理员
```

生产禁止：

- `app.demo-accounts.enabled=true`（演示账号仅 dev profile）
- `AUTH_MOCK_LOGIN_ENABLED=true`
- `PLATFORM_DEV_TOOLS_ENABLED=true`

#### 8.3 部署 jar 与 systemd 服务

```bash
sudo useradd --system --home /opt/game-companion --shell /usr/sbin/nologin game || true
sudo install -d -o game -g game /opt/game-companion
sudo cp server/target/game-companion-server-1.0.0-SNAPSHOT.jar \
  /opt/game-companion/game-companion-server.jar
sudo chown game:game /opt/game-companion/game-companion-server.jar
```


```ini
[Unit]
Description=PP电竞鼠 Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=game
Group=game
WorkingDirectory=/opt/game-companion
EnvironmentFile=/etc/game-companion/server.env
ExecStart=/usr/bin/java -Xms512m -Xmx1g -jar /opt/game-companion/game-companion-server.jar --spring.profiles.active=prod
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now game-companion
sudo systemctl status game-companion
curl http://127.0.0.1:8088/api/health
```

健康检查应返回：

```json
{"code":200,"data":{"name":"PP电竞鼠 (PP Esports)"}}
```

### 9. Web 前端与 Nginx

#### 9.1 构建与发布

```bash
cd web
npm ci
npm run build
sudo rm -rf /var/www/pp-esports
sudo mkdir -p /var/www/pp-esports
sudo cp -r dist/* /var/www/pp-esports/
```

#### 9.2 Nginx 配置

```nginx
server {
    listen 80;
    server_name web.你的域名;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name web.你的域名;

    ssl_certificate     /etc/letsencrypt/live/web.你的域名/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/web.你的域名/privkey.pem;

    root /var/www/pp-esports;
    index index.html;
    client_max_body_size 25m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8088/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";

        # SSE /chat/stream 必须关闭缓冲，避免消息被 Nginx 攒批
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

SSE 说明：

- 后端提供 `GET /api/chat/stream?token=...`
- 聊天消息与陪练新订单通过 SSE 推送
- 前端仍保留 5 秒/10 秒轮询兜底
- Nginx 必须关闭 `proxy_buffering`，后端已返回 `X-Accel-Buffering: no`
- token 通过查询参数传递，生产建议后续替换为短期 stream ticket

### 10. 微信支付与退款

按终端选择：

| 终端 | 微信支付产品 | 前端行为 |
| :--- | :--- | :--- |
| PC 浏览器 | Native 扫码 | 返回 `codeUrl`，前端展示二维码 |
| 手机浏览器（非微信） | H5 支付 | 返回 `h5Url`，跳转微信收银台 |
| 微信内浏览器 | 暂不支持 | 提示在外部浏览器打开 |

```bash
WX_PAY_APP_ID=与商户号绑定且开通对应产品的 AppID
WX_PAY_MCH_ID=微信支付商户号
WX_PAY_API_V3_KEY=32位APIv3密钥
WX_PAY_KEY_PATH=/opt/certs/apiclient_key.pem
WX_PAY_CERT_PATH=/opt/certs/apiclient_cert.pem
WX_PAY_NOTIFY_URL=https://web.你的域名/api/pay/notify
WX_PAY_WEB_APP_URL=https://web.你的域名
WX_PAY_TRANSFER_SCENE_ID=商家转账场景ID
```

注意：

- 仓库不包含任何商户私钥、API v3 密钥或证书
- H5 支付需在商户平台单独申请并配置已备案支付域名，不能用 `localhost`
- H5 支付不能在微信内置浏览器使用
- 退款原路退回，不是退现金；退款到账时间由微信决定
- 当前退款仅支持业务定义的全额退款场景：待接单客户撤回、接单超时、服务超时、管理员仲裁退款
- **上线前需补退款异步回调**：当前退款 `notify_url` 复用 `/api/pay/notify`，而该接口只解析支付通知；微信退款返回 `PROCESSING` 时，最终状态需要独立 `/pay/refund-notify` 或主动查询退款结果，否则可能出现平台已显示退款但微信仍在处理中的对账风险

### 11. 上线检查与排障

#### 11.1 上线前检查

- [ ] `init_schema.sql` 只用于新库，存量库已备份并执行迁移
- [ ] `sys_user.phone`、`pay_flow.order_sn` 唯一键已生效
- [ ] `chat_room`、`chat_read_state` 已建表并有历史回填
- [ ] `application-prod.yml` 生效，`dev-tools`、mock 登录、演示账号全部关闭
- [ ] 管理员通过 `BOOTSTRAP_ADMIN_*` 创建后已移除变量
- [ ] `CORS_ALLOWED_ORIGINS` 为正式域名，不使用 `*`
- [ ] HTTPS 证书有效，H5 支付域名与商户平台一致
- [ ] `WX_PAY_NOTIFY_URL` 公网可达
- [ ] Nginx 已关闭 SSE 缓冲，上传限制 25MB
- [ ] 8088、MySQL、Redis 未暴露公网
- [ ] 备份任务、日志轮转、systemd 自启已配置

#### 11.2 常用排障

```bash
# 后端日志
journalctl -u game-companion -f

# 健康检查
curl -s https://web.你的域名/api/health

# 检查 SSE（需要登录 token）
curl -N "https://web.你的域名/api/chat/stream?token=你的token"

# 检查 Nginx 配置
sudo nginx -t && sudo systemctl reload nginx
```

常见问题：

- 登录 401：检查 `satoken` 请求头、CORS、登录态过期
- 支付回调不到：检查域名、HTTPS、微信商户平台回调地址、Nginx 是否拦截 `/api/pay/notify`
- SSE 不实时：检查 Nginx `proxy_buffering off`、代理超时、token 是否有效
- 上传失败：检查 Nginx `client_max_body_size` 和后端 20MB/25MB 限制
- 数据库迁移失败：先查重复手机号、支付单号、技能、游戏名，按脚本提示人工合并
