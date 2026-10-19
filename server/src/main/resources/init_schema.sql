-- ========================================================
-- 游戏陪玩/开黑小程序 - 核心业务数据库设计
-- 引擎：InnoDB
-- 字符集：utf8mb4 (支持微信Emoji表情)
-- 金额约定：涉及金额统一使用整型（单位：分，避免浮点数精度问题）
-- ========================================================

CREATE DATABASE IF NOT EXISTS `game_companion` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `game_companion`;

-- --------------------------------------------------------
-- 1. 用户表 (双端通用：客户 / 陪玩师 / 管理员)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `openid` VARCHAR(64) DEFAULT NULL COMMENT '微信小程序 OpenID',
  `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信开放平台 UnionID (打通小程序与公众号)',
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
-- 5. 订单主表 (核心业务流转中心)
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
  `total_amount` INT NOT NULL COMMENT '订单总金额 (单位: 分)',
  `platform_fee` INT NOT NULL DEFAULT 0 COMMENT '平台抽佣金额 (单位: 分，例如10%)',
  `companion_income` INT NOT NULL DEFAULT 0 COMMENT '陪玩师最终结算所得 (单位: 分 = total - fee)',
  
  -- 订单类型与流转
  `order_type` TINYINT NOT NULL DEFAULT 1 COMMENT '订单类型：1-指定陪玩单 2-大厅公共抢单',
  `is_transferred` TINYINT NOT NULL DEFAULT 0 COMMENT '是否发生过超时流转：0-否 1-已转入大厅',
  `original_companion_id` BIGINT DEFAULT NULL COMMENT '原指定陪玩师ID (用于超时未接统计与降权)',
  
  -- 客户提交的游戏信息
  `game_server` VARCHAR(64) DEFAULT NULL COMMENT '游戏区服 (如: 微信区 / QQ区)',
  `game_account` VARCHAR(64) DEFAULT NULL COMMENT '游戏昵称/账号',
  `client_remark` VARCHAR(255) DEFAULT NULL COMMENT '客户特别要求 (如: 只要射手，来声音温柔的)',

  -- 订单状态机
  `order_status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态: 0-待付款 1-待接单 2-服务沟通中 3-申请结单 4-订单已完成 5-已取消 6-已退款',
  
  -- 支付相关
  `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0-未支付 1-已支付 2-已退款',
  `pay_type` VARCHAR(16) DEFAULT 'mock' COMMENT '支付渠道：mock(模拟支付) / wechat(微信支付)',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '支付通道流水单号 (微信支付单号或Mock单号)',
  `pay_time` DATETIME DEFAULT NULL COMMENT '付款完成时间',
  `accept_time` DATETIME DEFAULT NULL COMMENT '陪玩师接单时间',
  `complete_time` DATETIME DEFAULT NULL COMMENT '结单完成时间',
  `settle_time` DATETIME DEFAULT NULL COMMENT '佣金入账钱包时间',
  `finish_screenshot` VARCHAR(512) DEFAULT NULL COMMENT '陪玩师结单凭证/战绩截图',

  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_sn` (`order_sn`),
  KEY `idx_client` (`client_user_id`),
  KEY `idx_companion` (`companion_user_id`),
  KEY `idx_order_status` (`order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩预约订单主表';

-- --------------------------------------------------------
-- 6. 订单评价表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `order_review`;
CREATE TABLE `order_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id` BIGINT NOT NULL COMMENT '关联companion_order表ID',
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
-- 10. 接单提醒与消息推送日志表 (监控漏单与触达率)
-- --------------------------------------------------------
DROP TABLE IF EXISTS `notice_log`;
CREATE TABLE `notice_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `order_sn` VARCHAR(32) NOT NULL COMMENT '关联订单号',
  `target_user_id` BIGINT NOT NULL COMMENT '接收通知的陪玩师ID',
  `channel` TINYINT NOT NULL COMMENT '通知通道：1-微信小程序订阅消息 2-微信服务号模板消息 3-手机短信 4-企业微信',
  `content` TEXT DEFAULT NULL COMMENT '下发消息内容/JSON入参',
  `send_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0-失败 1-成功',
  `response_msg` VARCHAR(255) DEFAULT NULL COMMENT '第三方网关返回结果/错误码',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推送时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_sn` (`order_sn`),
  KEY `idx_target_user` (`target_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单通知下发日志表';

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

-- ========================================================
-- 初始基础数据 (游戏常用分类 + 初始预置账号与陪玩师)
-- ========================================================

-- 游戏分类字典
INSERT INTO `game_category` (`id`, `name`, `icon`, `sort`, `is_active`) VALUES
(1, '王者荣耀', 'https://img.icons8.com/color/96/honor-of-kings.png', 100, 1),
(2, '英雄联盟', 'https://img.icons8.com/color/96/league-of-legends.png', 90, 1),
(3, '和平精英', 'https://img.icons8.com/color/96/pubg.png', 80, 1),
(4, '无畏契约', 'https://img.icons8.com/color/96/valorant.png', 70, 1),
(5, '永劫无间', 'https://img.icons8.com/color/96/sword.png', 60, 1),
(6, '英雄联盟手游', 'https://img.icons8.com/color/96/riot-games.png', 50, 1);

-- 初始用户:
-- 10000: 超级管理员 (沉稳帅气电竞管理)
-- 20001: 甜心小鹿 (高颜值甜美东方软妹)
-- 20002: 野王阿肆 (阳光帅气东方野王)
-- 30001: 软妹软糖 (软萌清纯东方萝莉)
INSERT INTO `sys_user` (`id`, `openid`, `nickname`, `avatar_url`, `phone`, `gender`, `role`, `status`) VALUES
(10000, 'mock_openid_admin', '平台超级管理员', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80', '13800000000', 1, 9, 1),
(20001, 'mock_openid_companion_1', '甜心小鹿', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=300&q=80', '13800000001', 2, 2, 1),
(20002, 'mock_openid_companion_2', '野王阿肆', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=300&q=80', '13800000002', 1, 2, 1),
(30001, 'mock_openid_apply_1', '软妹软糖', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=300&q=80', '13800000003', 2, 1, 1);

-- 陪玩师档案主表 (音频全面升级为小程序本地原生秒播 MP3)
INSERT INTO `companion_profile` (`id`, `user_id`, `real_name`, `bio`, `voice_url`, `voice_duration`, `is_online`, `rating`, `order_count`, `audit_status`) VALUES
(1, 20001, '鹿小甜', '主玩软辅中单，听指挥超温柔，输赢都不压力~', '/static/audio/voice_sweet_girl.mp3', 6, 1, 5.0, 128, 1),
(2, 20002, '肆阿野', '全能打野带飞上分，控龙抓人意识顶级，稳稳吃分！', '/static/audio/voice_wild_boy.mp3', 4, 1, 4.9, 342, 1),
(3, 30001, '唐小软', '萌新陪练，主打陪伴和聊天，声音甜甜不发脾气！', '/static/audio/voice_soft_candy.mp3', 2, 0, 5.0, 0, 0);

-- 陪玩师技能表 (用于下单选择)
INSERT INTO `companion_skill` (`id`, `companion_id`, `user_id`, `game_id`, `game_name`, `rank_name`, `rank_img`, `price`, `unit`, `status`) VALUES
(1, 1, 20001, 1, '王者荣耀', '最强王者 35星', 'https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80', 2500, '小时', 1),
(2, 2, 20002, 1, '王者荣耀', '荣耀王者 68星', 'https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80', 3500, '局', 1),
(3, 1, 20001, 2, '英雄联盟', '傲世宗师', 'https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80', 3000, '小时', 1);

-- 派单大厅初始公共抢单
INSERT INTO `companion_order` (`id`, `order_sn`, `client_user_id`, `companion_user_id`, `skill_id`, `game_name`, `unit_price`, `quantity`, `total_amount`, `platform_fee`, `companion_income`, `order_type`, `is_transferred`, `game_server`, `game_account`, `client_remark`, `order_status`, `pay_status`, `pay_type`, `create_time`) VALUES
(1, '20261016102801992', 10000, NULL, 1, '王者荣耀', 2500, 2, 5000, 500, 4500, 2, 0, '微信大区 / 排位上分', '开黑老板666', '来个厉害的打野或者射手，连胜中勿坑！', 1, 1, 'mock', NOW());
