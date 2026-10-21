-- ========================================================
-- 游戏陪玩/开黑小程序 - 核心业务数据库设计
-- 引擎：InnoDB
-- 字符集：utf8mb4 (支持微信Emoji表情)
-- 金额约定：涉及金额统一使用整型（单位：分，避免浮点数精度问题）
-- ========================================================

-- ⚠️ 关键：必须先声明连接字符集，否则用 GBK/GB2312 客户端执行本脚本时，
--    中文游戏名会被写成乱码，导致按名称关联 companion_skill.game_id 全部失效
--    (表现为「所有游戏都显示成王者荣耀图标」)。请务必保留下面两行。
SET NAMES utf8mb4;
SET character_set_client = utf8mb4;

CREATE DATABASE IF NOT EXISTS `game_companion` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `game_companion`;

-- --------------------------------------------------------
-- 1. 用户表 (双端通用：客户 / 陪玩师 / 管理员)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `openid` VARCHAR(64) DEFAULT NULL COMMENT '微信小程序 OpenID',
  `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信开放平台 UnionID (注意：无UnionID时必须存为NULL，禁止存入空串以防唯一索引冲突)',
  `mp_openid` VARCHAR(64) DEFAULT NULL COMMENT '微信服务号 OpenID (用于下发公众号模板消息通知)',
  `nickname` VARCHAR(64) NOT NULL DEFAULT '微信用户' COMMENT '用户昵称',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号码 (用于接单短信兜底提醒)',
  `gender` TINYINT DEFAULT 0 COMMENT '性别：0-未知 1-男 2-女',
  `role` TINYINT NOT NULL DEFAULT 1 COMMENT '角色：1-普通客户 2-陪玩师 9-平台管理员',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：0-已封禁 1-正常',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  UNIQUE KEY `uk_unionid` (`unionid`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基础表';

-- --------------------------------------------------------
-- 2. 游戏品类字典表 (如：王者荣耀、英雄联盟、和平精英)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `game_category`;
CREATE TABLE `game_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '游戏品类ID',
  `name` VARCHAR(64) NOT NULL COMMENT '游戏名称',
  `icon` VARCHAR(512) DEFAULT NULL COMMENT '游戏图标ICON',
  `sort` INT DEFAULT 0 COMMENT '排序权重 (数字越大越靠前)',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏品类字典表';

-- --------------------------------------------------------
-- 3. 陪玩师主档表 (陪玩师专属信息)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `companion_profile`;
CREATE TABLE `companion_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '陪玩师档案ID',
  `user_id` BIGINT NOT NULL COMMENT '关联sys_user表ID',
  `real_name` VARCHAR(32) DEFAULT NULL COMMENT '实名姓名',
  `id_card` VARCHAR(32) DEFAULT NULL COMMENT '身份证号 (脱敏)',
  `bio` VARCHAR(255) DEFAULT NULL COMMENT '个性签名/陪玩简介',
  `voice_url` VARCHAR(512) DEFAULT NULL COMMENT '语音介绍录像/音频URL (展示声音特点)',
  `voice_duration` INT DEFAULT 0 COMMENT '语音时长(秒)',
  `photos` JSON DEFAULT NULL COMMENT '形象照/风采相册 (JSON数组字符串)',
  `is_online` TINYINT NOT NULL DEFAULT 1 COMMENT '接单状态：0-休息中/勿扰 1-接单中',
  `rating` DECIMAL(2,1) NOT NULL DEFAULT 5.0 COMMENT '综合评分 (1.0 - 5.0)',
  `order_count` INT NOT NULL DEFAULT 0 COMMENT '已接单总量',
  `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '入驻审核状态：0-待审核 1-已通过 2-已驳回',
  `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT '审核驳回原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入驻申请时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_is_online` (`is_online`),
  KEY `idx_audit_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师资料主表';

-- --------------------------------------------------------
-- 4. 陪玩师技能项目表 (某一陪玩师擅长的游戏与定价)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `companion_skill`;
CREATE TABLE `companion_skill` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '技能ID',
  `companion_id` BIGINT NOT NULL COMMENT '关联companion_profile表ID',
  `user_id` BIGINT NOT NULL COMMENT '陪玩师的sys_user_id',
  `game_id` BIGINT NOT NULL COMMENT '关联game_category表ID',
  `game_name` VARCHAR(64) DEFAULT NULL COMMENT '游戏名称冗余',
  `rank_name` VARCHAR(64) DEFAULT NULL COMMENT '游戏当前段位 (如: 最强王者 50星)',
  `rank_img` VARCHAR(512) DEFAULT NULL COMMENT '段位认证截图',
  `price` INT NOT NULL COMMENT '单价 (单位：分，如 2500 代表 25.00元)',
  `unit` VARCHAR(16) NOT NULL DEFAULT '小时' COMMENT '计价单位：小时 / 局',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态：0-下架 1-上架',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_companion_id` (`companion_id`),
  KEY `idx_game_id` (`game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师技能与定价表';

-- --------------------------------------------------------
-- 5. 订单主表 (纯粹业务交易与履约域，彻底解耦资金流与清算流)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `companion_order`;
CREATE TABLE `companion_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单主键ID',
  `order_sn` VARCHAR(32) NOT NULL COMMENT '业务订单号 (年月日时分秒+随机串，全局唯一)',
  `client_user_id` BIGINT NOT NULL COMMENT '下单客户 user_id',
  `companion_user_id` BIGINT DEFAULT NULL COMMENT '接单陪玩师 user_id',
  `skill_id` BIGINT NOT NULL COMMENT '关联服务技能 companion_skill_id',
  `game_name` VARCHAR(64) NOT NULL COMMENT '冗余字段: 游戏名称 (如: 王者荣耀)',
  `unit_price` INT NOT NULL COMMENT '下单单价 (单位: 分)',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '购买数量 (如: 2局 或 2小时)',
  `original_amount` INT NOT NULL DEFAULT 0 COMMENT '订单标价总额 (分 = 单价 x 数量)',
  `discount_amount` INT NOT NULL DEFAULT 0 COMMENT '优惠立减金额 (分，默认0，预留优惠券/补贴扩展)',
  `total_amount` INT NOT NULL COMMENT '买家应付/实付总额 (分 = original - discount)',
  
  -- 订单类型与流转
  `order_type` TINYINT NOT NULL DEFAULT 1 COMMENT '订单类型：1-指定陪玩单 2-大厅公共抢单',
  `is_transferred` TINYINT NOT NULL DEFAULT 0 COMMENT '是否发生过超时流转：0-否 1-已转入大厅',
  `original_companion_id` BIGINT DEFAULT NULL COMMENT '原指定陪玩师ID (用于超时未接统计与降权)',
  
  -- 客户提交的游戏信息
  `game_server` VARCHAR(64) DEFAULT NULL COMMENT '游戏区服 (如: 微信区 / QQ区)',
  `game_account` VARCHAR(64) DEFAULT NULL COMMENT '游戏昵称/账号',
  `client_remark` VARCHAR(255) DEFAULT NULL COMMENT '客户特别要求 (如: 只要射手，来声音温柔的)',

  -- 订单履约状态机 (纯业务流)
  `order_status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态: 0-待付款 1-待接单 2-服务沟通中 3-申请结单 4-订单已完成 5-已取消 6-已退款 7-维权争议中 8-仲裁退款完成',
  
  -- 履约过程时间与凭证
  `accept_time` DATETIME DEFAULT NULL COMMENT '陪玩师接单时间',
  `complete_time` DATETIME DEFAULT NULL COMMENT '结单完成时间',
  `finish_screenshot` VARCHAR(512) DEFAULT NULL COMMENT '陪玩师结单凭证/战绩截图',

  -- 售后维权与平台仲裁字段
  `dispute_reason` VARCHAR(255) DEFAULT NULL COMMENT '买家发起维权争议原因',
  `dispute_time` DATETIME DEFAULT NULL COMMENT '发起争议维权时间',
  `dispute_result` VARCHAR(255) DEFAULT NULL COMMENT '平台管理员仲裁处理说明',

  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_sn` (`order_sn`),
  KEY `idx_client` (`client_user_id`),
  KEY `idx_companion` (`companion_user_id`),
  KEY `idx_order_status` (`order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩预约订单主表 (纯业务履约域)';

-- --------------------------------------------------------
-- 6. 订单评价表 (买家完结后打分评价)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `order_review`;
CREATE TABLE `order_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id` BIGINT NOT NULL COMMENT '关联companion_order表ID',
  `order_sn` VARCHAR(32) NOT NULL COMMENT '关联业务订单号',
  `client_user_id` BIGINT NOT NULL COMMENT '评价人(客户)',
  `companion_user_id` BIGINT NOT NULL COMMENT '被评价人(陪玩师)',
  `score` TINYINT NOT NULL DEFAULT 5 COMMENT '综合评分 (1-5星)',
  `skill_score` TINYINT DEFAULT 5 COMMENT '游戏技术评分 (1-5星)',
  `attitude_score` TINYINT DEFAULT 5 COMMENT '服务态度评分 (1-5星)',
  `tags` VARCHAR(255) DEFAULT NULL COMMENT '快捷标签 (如: 声音好听,意识超群,耐心温柔)',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '评价具体文字',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_order_sn` (`order_sn`),
  KEY `idx_companion_user_id` (`companion_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价表';

-- --------------------------------------------------------
-- 7. 资金钱包表 (陪玩师佣金与提现)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `user_wallet`;
CREATE TABLE `user_wallet` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '钱包ID',
  `user_id` BIGINT NOT NULL COMMENT '关联sys_user表ID',
  `balance` INT NOT NULL DEFAULT 0 COMMENT '可提现余额 (单位：分)',
  `frozen_balance` INT NOT NULL DEFAULT 0 COMMENT '提现中冻结金额 (单位：分)',
  `total_income` INT NOT NULL DEFAULT 0 COMMENT '历史累计总收益 (单位：分)',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号 (并发安全控制)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资金钱包表';

-- --------------------------------------------------------
-- 8. 钱包资金变动流水表 (账目对账保证)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `wallet_flow`;
CREATE TABLE `wallet_flow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `flow_sn` VARCHAR(32) NOT NULL COMMENT '流水单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `type` TINYINT NOT NULL COMMENT '变动类型：1-订单收益结算 2-发起提现冻结 3-提现成功扣除 4-提现驳回解冻',
  `amount` INT NOT NULL COMMENT '变动金额 (单位：分，正数增加，负数扣减)',
  `before_balance` INT NOT NULL COMMENT '变动前余额 (单位：分)',
  `after_balance` INT NOT NULL COMMENT '变动后余额 (单位：分)',
  `order_sn` VARCHAR(32) DEFAULT NULL COMMENT '关联订单号 (若是订单结算)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '流水备注说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sn` (`flow_sn`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包资金变动明细表';

-- --------------------------------------------------------
-- 9. 提现申请记录表 (对接微信“商家转账到零钱”)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `withdraw_record`;
CREATE TABLE `withdraw_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提现记录ID',
  `withdraw_sn` VARCHAR(32) NOT NULL COMMENT '提现流水号',
  `user_id` BIGINT NOT NULL COMMENT '申请陪玩师 user_id',
  `amount` INT NOT NULL COMMENT '申请提现金额 (单位: 分)',
  `fee` INT NOT NULL DEFAULT 0 COMMENT '提现手续费 (单位: 分)',
  `actual_amount` INT NOT NULL COMMENT '实际到账金额 (单位: 分)',
  `channel` VARCHAR(16) NOT NULL DEFAULT 'mock' COMMENT '提现渠道: mock / wechat_transfer',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '提现状态：0-申请中/待审核 1-打款中 2-提现成功 3-提现失败/已驳回',
  `wechat_batch_id` VARCHAR(64) DEFAULT NULL COMMENT '微信商家转账批次单号',
  `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败或驳回原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `finish_time` DATETIME DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_withdraw_sn` (`withdraw_sn`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现记录表';

-- --------------------------------------------------------
-- 10. 平台线下通知事件队列表 (替代已下线的微信一次性订阅消息)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `notice_log`;
CREATE TABLE `notice_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `order_sn` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '关联订单号 (无订单关联的事件为空串)',
  `target_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '需要被通知的用户ID (陪玩师/入驻申请人)',
  `notice_type` TINYINT NOT NULL DEFAULT 1 COMMENT '通知类型: 1-新订单待接单 2-陪玩师已接单 3-入驻审核结果 4-售后维权',
  `channel` TINYINT NOT NULL DEFAULT 5 COMMENT '通知渠道: 5-平台线下通知 (电话/微信/短信)',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '事件标题',
  `content` TEXT DEFAULT NULL COMMENT '事件内容/线下通知话术',
  `user_name` VARCHAR(64) DEFAULT NULL COMMENT '被通知人昵称',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '被通知人手机号 (供平台线下拨打)',
  `amount` INT DEFAULT NULL COMMENT '关联金额 (分)',
  `send_status` TINYINT NOT NULL DEFAULT 0 COMMENT '通知状态: 0-待通知 1-已通知',
  `response_msg` VARCHAR(255) DEFAULT NULL COMMENT '管理员处理备注 (通知方式/时间)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件发生时间',
  `finish_time` DATETIME DEFAULT NULL COMMENT '通知完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_sn` (`order_sn`),
  KEY `idx_target_user` (`target_user_id`),
  KEY `idx_send_status` (`send_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台线下通知事件队列 (替代已下线的微信订阅消息)';

-- --------------------------------------------------------
-- 11. 开黑房间即时聊天记录表 (真实双向通信)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_sn` VARCHAR(32) NOT NULL COMMENT '关联订单号',
  `sender_id` BIGINT NOT NULL COMMENT '发送人ID',
  `sender_name` VARCHAR(64) DEFAULT NULL COMMENT '发送人昵称',
  `sender_avatar` VARCHAR(512) DEFAULT NULL COMMENT '发送人头像',
  `content` TEXT NOT NULL COMMENT '消息正文',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开黑房间即时聊天记录表';

-- --------------------------------------------------------
-- 12. 平台系统动态配置表 (用于管理员在线修改微信支付与抽成参数)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` VARCHAR(64) NOT NULL COMMENT '配置键名',
  `config_value` TEXT DEFAULT NULL COMMENT '配置内容',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '说明备注',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台系统全局动态配置表';

-- --------------------------------------------------------
-- 13. 支付流水事实表 (金融级支付域解耦：承载真实支付事实与通道扣款)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `pay_flow`;
CREATE TABLE `pay_flow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pay_sn` VARCHAR(32) NOT NULL COMMENT '内部支付流水单号 (PAY_xxx)',
  `order_sn` VARCHAR(32) NOT NULL COMMENT '关联业务订单号',
  `client_user_id` BIGINT NOT NULL COMMENT '付款人用户ID',
  `pay_type` VARCHAR(16) NOT NULL DEFAULT 'mock' COMMENT '支付渠道：mock / wechat_pay / alipay',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '第三方支付机构流水单号 (微信商户交易单号)',
  `pay_amount` INT NOT NULL COMMENT '实际付款金额 (单位: 分)',
  `channel_fee` INT NOT NULL DEFAULT 0 COMMENT '第三方通道手续费扣除 (微信0.6%，单位: 分，支付成功时落盘)',
  `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付 1-支付成功 2-支付失败 3-已全额退款 4-部分退款',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付成功到账时间',
  `refund_amount` INT DEFAULT 0 COMMENT '已退款金额 (单位: 分)',
  `refund_channel_fee` INT DEFAULT 0 COMMENT '退款冲正的通道费 (单位: 分)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_sn` (`pay_sn`),
  KEY `idx_order_sn` (`order_sn`),
  KEY `idx_transaction_id` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水事实表';

-- --------------------------------------------------------
-- 14. 订单佣金分润清算表 (履约完结时正式生成，绝不早产)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `order_settlement`;
CREATE TABLE `order_settlement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `settle_sn` VARCHAR(32) NOT NULL COMMENT '清算流水号 (SETTLE_xxx)',
  `order_sn` VARCHAR(32) NOT NULL COMMENT '关联业务订单号',
  `client_user_id` BIGINT NOT NULL COMMENT '客户ID',
  `companion_user_id` BIGINT NOT NULL COMMENT '陪练师ID',
  `total_amount` INT NOT NULL COMMENT '订单总交易额 (单位: 分)',
  `companion_income` INT NOT NULL COMMENT '陪练师最终结算入账所得 (单位: 分)',
  `platform_gross_fee` INT NOT NULL COMMENT '平台账面毛抽佣 (单位: 分)',
  `channel_fee` INT NOT NULL DEFAULT 0 COMMENT '扣除的第三方微信通道费 (单位: 分，按0.6%核算)',
  `platform_net_profit` INT NOT NULL COMMENT '平台真实到手净利润 (单位: 分 = gross_fee - channel_fee)',
  `fee_payer` TINYINT NOT NULL DEFAULT 1 COMMENT '通道费承担主体：1-平台利润承担 2-陪练师承担 3-买家附加承担',
  `settle_status` TINYINT NOT NULL DEFAULT 1 COMMENT '清算状态：1-已清算入账 2-已冲正驳回',
  `settle_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '清算完成时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settle_sn` (`settle_sn`),
  UNIQUE KEY `uk_settle_order_sn` (`order_sn`),
  KEY `idx_companion_user_id` (`companion_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单佣金分润清算表';

-- ========================================================
-- 初始基础数据 (仅保留 6 大游戏品类字典，用户与订单数据全量清空，支持真实微信号扫码注册)
-- ========================================================

-- 游戏分类字典
-- ⚠️ 图标统一使用小程序包内静态目录 /static/game/*.png：
--    1) 第三方图床会随时下线导致 404；2) 第三方域名在小程序正式版需加 downloadFile 合法域名白名单
--    静态资源随包发布，零网络依赖、零白名单配置
INSERT INTO `game_category` (`id`, `name`, `icon`, `sort`, `is_active`) VALUES
(1, '王者荣耀', '/static/game/honor-of-kings.png', 100, 1),
(2, '英雄联盟', '/static/game/league-of-legends.png', 90, 1),
(3, '和平精英', '/static/game/pubg.png', 80, 1),
(4, '无畏契约', '/static/game/valorant.png', 70, 1),
(5, '永劫无间', '/static/game/sword.png', 60, 1),
(6, '英雄联盟手游', '/static/game/riot-games.png', 50, 1);
