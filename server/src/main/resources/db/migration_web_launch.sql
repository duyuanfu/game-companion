-- Web 上线 / 数据完整性加固迁移
-- 建议使用 mysql CLI 执行，脚本可重复执行；执行前请备份数据库。
-- 如存在重复手机号 / 支付单号 / 技能 / 游戏名称，脚本会中止并提示，需人工合并。

DROP PROCEDURE IF EXISTS gc_add_column;
DROP PROCEDURE IF EXISTS gc_add_index;
DROP PROCEDURE IF EXISTS gc_add_unique_index;
DROP PROCEDURE IF EXISTS gc_drop_index;

DELIMITER $$
CREATE PROCEDURE gc_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(1000))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE gc_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE gc_add_unique_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE gc_drop_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL gc_add_column('sys_user', 'password_hash', '`password_hash` VARCHAR(100) DEFAULT NULL COMMENT ''Web 登录密码 BCrypt 哈希'' AFTER `phone`');

-- 空字符串手机号统一转为 NULL，避免唯一索引把多个空串判为重复
UPDATE `sys_user` SET `phone` = NULL WHERE `phone` = '';

DROP PROCEDURE IF EXISTS gc_assert_unique_phone;
DELIMITER $$
CREATE PROCEDURE gc_assert_unique_phone()
BEGIN
    DECLARE dup INT DEFAULT 0;
    SELECT COUNT(*) INTO dup FROM (SELECT phone FROM sys_user
        WHERE phone IS NOT NULL AND phone <> '' GROUP BY phone HAVING COUNT(*) > 1) t;
    IF dup > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_user.phone 存在重复值，请先人工合并账号';
    END IF;
END$$
DELIMITER ;
CALL gc_assert_unique_phone();
CALL gc_add_unique_index('sys_user', 'uk_phone', '`phone`');
CALL gc_drop_index('sys_user', 'idx_phone');

CALL gc_add_column('withdraw_record', 'payout_remark', '`payout_remark` VARCHAR(255) DEFAULT NULL COMMENT ''人工打款备注/流水号'' AFTER `channel`');
CALL gc_add_column('withdraw_record', 'payout_voucher', '`payout_voucher` VARCHAR(512) DEFAULT NULL COMMENT ''人工打款凭证 URL'' AFTER `payout_remark`');
ALTER TABLE `withdraw_record`
    MODIFY COLUMN `channel` VARCHAR(16) NOT NULL DEFAULT 'manual'
    COMMENT '打款渠道: manual / wechat_transfer / mock';
UPDATE `withdraw_record` SET `channel` = 'manual'
WHERE `status` = 0 AND `channel` IN ('mock', 'wechat');

DROP PROCEDURE IF EXISTS gc_assert_unique_pay_order;
DELIMITER $$
CREATE PROCEDURE gc_assert_unique_pay_order()
BEGIN
    DECLARE dup INT DEFAULT 0;
    SELECT COUNT(*) INTO dup FROM (SELECT order_sn FROM pay_flow GROUP BY order_sn HAVING COUNT(*) > 1) t;
    IF dup > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pay_flow.order_sn 存在重复支付流水，请先人工核对';
    END IF;
END$$
DELIMITER ;
CALL gc_assert_unique_pay_order();
CALL gc_add_unique_index('pay_flow', 'uk_pay_order_sn', '`order_sn`');
CALL gc_drop_index('pay_flow', 'idx_order_sn');

CALL gc_add_column('chat_message', 'message_type', '`message_type` TINYINT NOT NULL DEFAULT 0 COMMENT ''消息类型：0-用户消息 1-系统消息'' AFTER `sender_id`');
UPDATE `chat_message` SET `message_type` = 1 WHERE `sender_id` = 0;
CALL gc_add_index('chat_message', 'idx_room_id', '`order_sn`, `id`');

CREATE TABLE IF NOT EXISTS `chat_read_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `room_sn` VARCHAR(32) NOT NULL COMMENT '聊天室标识',
  `last_read_time` DATETIME NOT NULL COMMENT '最后一次已读时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_room` (`user_id`, `room_sn`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天室已读状态表';

CREATE TABLE IF NOT EXISTS `chat_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `room_sn` VARCHAR(32) NOT NULL,
  `room_type` TINYINT NOT NULL DEFAULT 1,
  `order_sn` VARCHAR(32) DEFAULT NULL,
  `user_a_id` BIGINT DEFAULT NULL,
  `user_b_id` BIGINT DEFAULT NULL,
  `last_message_id` BIGINT DEFAULT NULL,
  `last_message_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_sn` (`room_sn`),
  KEY `idx_user_a` (`user_a_id`),
  KEY `idx_user_b` (`user_b_id`),
  KEY `idx_last_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天室摘要表';

-- 回填订单房间
INSERT INTO `chat_room`
  (`room_sn`, `room_type`, `order_sn`, `user_a_id`, `user_b_id`, `last_message_id`, `last_message_time`, `create_time`, `update_time`)
SELECT m.order_sn, 1, m.order_sn, o.client_user_id, o.companion_user_id,
       m.id, m.create_time, m.create_time, m.create_time
FROM `chat_message` m
JOIN `companion_order` o ON o.order_sn = m.order_sn
JOIN (
  SELECT order_sn, MAX(id) AS max_id
  FROM `chat_message`
  WHERE LEFT(order_sn, 8) <> 'CONSULT_'
  GROUP BY order_sn
) latest ON latest.max_id = m.id
ON DUPLICATE KEY UPDATE
  `room_type` = VALUES(`room_type`),
  `order_sn` = VALUES(`order_sn`),
  `user_a_id` = VALUES(`user_a_id`),
  `user_b_id` = VALUES(`user_b_id`),
  `last_message_id` = VALUES(`last_message_id`),
  `last_message_time` = VALUES(`last_message_time`);

-- 回填咨询房间
INSERT INTO `chat_room`
  (`room_sn`, `room_type`, `order_sn`, `user_a_id`, `user_b_id`, `last_message_id`, `last_message_time`, `create_time`, `update_time`)
SELECT latest.order_sn, 2, NULL,
       CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(latest.order_sn, '_', 2), '_', -1) AS UNSIGNED),
       CAST(SUBSTRING_INDEX(latest.order_sn, '_', -1) AS UNSIGNED),
       m.id, m.create_time, m.create_time, m.create_time
FROM (
  SELECT order_sn, MAX(id) AS max_id
  FROM `chat_message`
  WHERE LEFT(order_sn, 8) = 'CONSULT_'
  GROUP BY order_sn
) latest
JOIN `chat_message` m ON m.id = latest.max_id
ON DUPLICATE KEY UPDATE
  `room_type` = VALUES(`room_type`),
  `user_a_id` = VALUES(`user_a_id`),
  `user_b_id` = VALUES(`user_b_id`),
  `last_message_id` = VALUES(`last_message_id`),
  `last_message_time` = VALUES(`last_message_time`);

CALL gc_add_column('companion_skill', 'update_time', '`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''');

DROP PROCEDURE IF EXISTS gc_assert_unique_skill;
DELIMITER $$
CREATE PROCEDURE gc_assert_unique_skill()
BEGIN
    DECLARE dup INT DEFAULT 0;
    SELECT COUNT(*) INTO dup FROM (SELECT user_id, game_name FROM companion_skill
        WHERE game_name IS NOT NULL GROUP BY user_id, game_name HAVING COUNT(*) > 1) t;
    IF dup > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'companion_skill 存在同一陪练同一游戏的重复技能';
    END IF;
END$$
DELIMITER ;
CALL gc_assert_unique_skill();
CALL gc_add_unique_index('companion_skill', 'uk_user_game', '`user_id`, `game_name`');

DROP PROCEDURE IF EXISTS gc_assert_unique_game_name;
DELIMITER $$
CREATE PROCEDURE gc_assert_unique_game_name()
BEGIN
    DECLARE dup INT DEFAULT 0;
    SELECT COUNT(*) INTO dup FROM (SELECT name FROM game_category GROUP BY name HAVING COUNT(*) > 1) t;
    IF dup > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'game_category.name 存在重复游戏名称';
    END IF;
END$$
DELIMITER ;
CALL gc_assert_unique_game_name();
CALL gc_add_unique_index('game_category', 'uk_name', '`name`');

DROP PROCEDURE IF EXISTS gc_add_column;
DROP PROCEDURE IF EXISTS gc_add_index;
DROP PROCEDURE IF EXISTS gc_add_unique_index;
DROP PROCEDURE IF EXISTS gc_drop_index;
DROP PROCEDURE IF EXISTS gc_assert_unique_phone;
DROP PROCEDURE IF EXISTS gc_assert_unique_pay_order;
DROP PROCEDURE IF EXISTS gc_assert_unique_skill;
DROP PROCEDURE IF EXISTS gc_assert_unique_game_name;
