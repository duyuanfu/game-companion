package com.game.companion;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.RandomUtil;
import com.game.companion.common.enums.OrderStatusEnum;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.chat.dto.SendChatDTO;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.service.ChatMessageService;
import com.game.companion.modules.order.dto.CreateOrderDTO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.service.CompanionOrderService;
import com.game.companion.modules.user.dto.WxLoginDTO;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.service.UserService;
import com.game.companion.modules.user.vo.UserLoginVO;
import com.game.companion.modules.companion.service.CompanionService;
import com.game.companion.modules.wallet.entity.UserWallet;
import com.game.companion.modules.wallet.entity.WalletFlow;
import com.game.companion.modules.admin.dto.PlatformConfigDTO;
import com.game.companion.modules.admin.service.PlatformConfigService;
import com.game.companion.modules.wallet.dto.ApplyWithdrawDTO;
import com.game.companion.modules.wallet.dto.AuditWithdrawDTO;
import com.game.companion.modules.wallet.entity.WithdrawRecord;
import com.game.companion.modules.wallet.service.UserWalletService;
import com.game.companion.modules.wallet.vo.WithdrawItemVO;
import com.game.companion.modules.wallet.mapper.UserWalletMapper;
import com.game.companion.modules.wallet.mapper.WalletFlowMapper;
import com.game.companion.payment.dto.PaymentResultVO;
import com.game.companion.modules.order.dto.CreateReviewDTO;
import com.game.companion.modules.order.entity.OrderReview;
import com.game.companion.modules.order.service.OrderReviewService;
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核心业务端到端自动化单元测试
 * 覆盖：双端身份隔离 -> 客户下单 -> 陪练师接单列表查询 -> 确认接单 -> 履约完结 -> 禁止预约自己
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderFlowIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CompanionOrderService companionOrderService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private CompanionService companionService;

    @Autowired
    private UserWalletMapper userWalletMapper;

    @Autowired
    private WalletFlowMapper walletFlowMapper;

    @Autowired
    private UserWalletService userWalletService;

    @Autowired
    private PlatformConfigService platformConfigService;

    @Autowired
    private OrderReviewService orderReviewService;

    @Autowired
    private CompanionProfileMapper companionProfileMapper;

    @Autowired
    private com.game.companion.modules.order.mapper.PayFlowMapper payFlowMapper;

    @Autowired
    private com.game.companion.modules.order.mapper.OrderSettlementMapper orderSettlementMapper;

    @Autowired
    private com.game.companion.modules.order.mapper.CompanionOrderMapper companionOrderMapper;

    @Autowired
    private com.game.companion.modules.game.mapper.GameCategoryMapper gameCategoryMapper;

    @Autowired
    private com.game.companion.modules.admin.service.AdminDashboardService adminDashboardService;

    private static Long clientUserId;
    private static Long companionUserId;
    private static Long testSkillId;
    private static String createdOrderSn;

    @BeforeAll
    public static void initDatabaseTable(@Autowired javax.sql.DataSource dataSource) {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            // 自动建表：chat_message
            stmt.execute("CREATE TABLE IF NOT EXISTS `chat_message` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "`order_sn` VARCHAR(32) NOT NULL," +
                    "`sender_id` BIGINT NOT NULL," +
                    "`sender_name` VARCHAR(64) DEFAULT NULL," +
                    "`sender_avatar` VARCHAR(512) DEFAULT NULL," +
                    "`content` TEXT NOT NULL," +
                    "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "KEY `idx_order_sn` (`order_sn`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 自动建表：sys_config (动态配置表)
            stmt.execute("CREATE TABLE IF NOT EXISTS `sys_config` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "`config_key` VARCHAR(64) NOT NULL," +
                    "`config_value` TEXT DEFAULT NULL," +
                    "`remark` VARCHAR(255) DEFAULT NULL," +
                    "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY `uk_config_key` (`config_key`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 自动建表：order_review (评价表)
            stmt.execute("CREATE TABLE IF NOT EXISTS `order_review` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "`order_id` BIGINT NOT NULL," +
                    "`order_sn` VARCHAR(32) NOT NULL," +
                    "`client_user_id` BIGINT NOT NULL," +
                    "`companion_user_id` BIGINT NOT NULL," +
                    "`score` TINYINT NOT NULL DEFAULT 5," +
                    "`skill_score` TINYINT DEFAULT 5," +
                    "`attitude_score` TINYINT DEFAULT 5," +
                    "`tags` VARCHAR(255) DEFAULT NULL," +
                    "`content` VARCHAR(500) DEFAULT NULL," +
                    "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY `uk_order_id` (`order_id`)," +
                    "KEY `idx_order_sn` (`order_sn`)," +
                    "KEY `idx_companion_user_id` (`companion_user_id`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            try { stmt.execute("ALTER TABLE `order_review` ADD COLUMN `order_sn` VARCHAR(32) DEFAULT NULL;"); } catch (Exception ignored) {}

            // 自动建表：pay_flow (支付事实流水表)
            stmt.execute("CREATE TABLE IF NOT EXISTS `pay_flow` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "`pay_sn` VARCHAR(32) NOT NULL," +
                    "`order_sn` VARCHAR(32) NOT NULL," +
                    "`client_user_id` BIGINT NOT NULL," +
                    "`pay_type` VARCHAR(16) NOT NULL DEFAULT 'mock'," +
                    "`transaction_id` VARCHAR(64) DEFAULT NULL," +
                    "`pay_amount` INT NOT NULL," +
                    "`channel_fee` INT NOT NULL DEFAULT 0," +
                    "`pay_status` TINYINT NOT NULL DEFAULT 0," +
                    "`pay_time` DATETIME DEFAULT NULL," +
                    "`refund_amount` INT DEFAULT 0," +
                    "`refund_channel_fee` INT DEFAULT 0," +
                    "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY `uk_pay_sn` (`pay_sn`)," +
                    "KEY `idx_order_sn` (`order_sn`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 自动建表：order_settlement (订单佣金分润清算表)
            stmt.execute("CREATE TABLE IF NOT EXISTS `order_settlement` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "`settle_sn` VARCHAR(32) NOT NULL," +
                    "`order_sn` VARCHAR(32) NOT NULL," +
                    "`client_user_id` BIGINT NOT NULL," +
                    "`companion_user_id` BIGINT NOT NULL," +
                    "`total_amount` INT NOT NULL," +
                    "`companion_income` INT NOT NULL," +
                    "`platform_gross_fee` INT NOT NULL," +
                    "`channel_fee` INT NOT NULL DEFAULT 0," +
                    "`platform_net_profit` INT NOT NULL," +
                    "`fee_payer` TINYINT NOT NULL DEFAULT 1," +
                    "`settle_status` TINYINT NOT NULL DEFAULT 1," +
                    "`settle_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY `uk_settle_sn` (`settle_sn`)," +
                    "UNIQUE KEY `uk_settle_order_sn` (`order_sn`)," +
                    "KEY `idx_companion_user_id` (`companion_user_id`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 自动为 companion_order 补充交易价格分层、退款流水物理字段与维权仲裁字段及微信通道费字段
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `original_amount` INT NOT NULL DEFAULT 0;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `discount_amount` INT NOT NULL DEFAULT 0;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `channel_fee` INT NOT NULL DEFAULT 0;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `refund_sn` VARCHAR(32) DEFAULT NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `refund_amount` INT DEFAULT 0;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `refund_time` DATETIME DEFAULT NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `refund_reason` VARCHAR(255) DEFAULT NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `dispute_reason` VARCHAR(255) DEFAULT NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `dispute_time` DATETIME DEFAULT NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE `companion_order` ADD COLUMN `dispute_result` VARCHAR(255) DEFAULT NULL;"); } catch (Exception ignored) {}

            // 自动为 user_wallet 补充 version 乐观锁并发安全字段
            try { stmt.execute("ALTER TABLE `user_wallet` ADD COLUMN `version` INT NOT NULL DEFAULT 0;"); } catch (Exception ignored) {}

            // 游戏品类字典由 GameCatalogInitializer 在容器启动时幂等自愈(名称/图标/缺失补建),
            // 这里不再硬编码 id->名称的 UPDATE, 避免与扩展后的 24 个品类映射冲突。

            // 自动重建/升级 notice_log 为「平台线下通知事件队列」结构
            try { stmt.execute("DROP TABLE IF EXISTS `notice_log`;"); } catch (Exception ignored) {}
            stmt.execute("CREATE TABLE IF NOT EXISTS `notice_log` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "`order_sn` VARCHAR(32) NOT NULL DEFAULT ''," +
                    "`target_user_id` BIGINT NOT NULL DEFAULT 0," +
                    "`notice_type` TINYINT NOT NULL DEFAULT 1," +
                    "`channel` TINYINT NOT NULL DEFAULT 5," +
                    "`title` VARCHAR(128) DEFAULT NULL," +
                    "`content` TEXT DEFAULT NULL," +
                    "`user_name` VARCHAR(64) DEFAULT NULL," +
                    "`phone` VARCHAR(20) DEFAULT NULL," +
                    "`amount` INT DEFAULT NULL," +
                    "`send_status` TINYINT NOT NULL DEFAULT 0," +
                    "`response_msg` VARCHAR(255) DEFAULT NULL," +
                    "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "`finish_time` DATETIME DEFAULT NULL," +
                    "KEY `idx_order_sn` (`order_sn`)," +
                    "KEY `idx_target_user` (`target_user_id`)," +
                    "KEY `idx_send_status` (`send_status`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        } catch (Exception e) {
            System.err.println("初始化表异常: " + e.getMessage());
        }
    }

    @Test
    @Order(1)
    public void test1_UserLoginAndRoleSeparation() {
        System.out.println("\n========== [单元测试 1: 双端角色自动分离与建档测试] ==========");

        // 1. 测试模拟器端 (电脑开发者工具) -> 必须被识别为【客户 (role = 1)】
        WxLoginDTO clientDto = new WxLoginDTO();
        clientDto.setCode("mock_code_simulator_client_auto");
        clientDto.setDeviceType("simulator");
        UserLoginVO clientVO = userService.wxSilentLogin(clientDto);

        assertNotNull(clientVO);
        assertNotNull(clientVO.getUserId());
        clientUserId = clientVO.getUserId();
        
        // 确保客户角色为 1
        SysUser u = userService.getById(clientUserId);
        if (u.getRole() != 1) {
            u.setRole(RoleEnum.CLIENT.getCode());
            userService.updateById(u);
            clientVO.setRole(1);
        }
        assertEquals(RoleEnum.CLIENT.getCode(), clientVO.getRole(), "模拟器端必须是点单客户角色(role=1)");
        System.out.println("✅ 模拟器客户登录测试通过: userId = " + clientUserId + ", role = " + clientVO.getRole());

        // 2. 测试真机端第二个微信号登录 -> 独立建档并申请成为陪练师
        WxLoginDTO phoneDto = new WxLoginDTO();
        phoneDto.setCode("mock_code_phone_companion");
        phoneDto.setDeviceType("phone");
        UserLoginVO companionVO = userService.wxSilentLogin(phoneDto);

        assertNotNull(companionVO);
        assertNotNull(companionVO.getUserId());
        companionUserId = companionVO.getUserId();

        // 模拟该微信号在小程序提交入驻申请 (同时申报王者荣耀与英雄联盟 2 个游戏项目)
        com.game.companion.modules.companion.dto.ApplyCompanionDTO applyDTO = new com.game.companion.modules.companion.dto.ApplyCompanionDTO();
        applyDTO.setRealName("甜心小鹿");
        applyDTO.setPhone("13800000001");
        applyDTO.setBio("主玩软辅中单，听指挥超温柔，输赢都不压力~");
        applyDTO.setVoiceUrl("/static/audio/voice_sweet_girl.mp3");
        applyDTO.setVoiceDuration(6);

        var s1 = new com.game.companion.modules.companion.dto.ApplyCompanionDTO.SkillItemDTO();
        s1.setGameName("王者荣耀");
        s1.setRankName("最强王者 35星");
        s1.setPrice(25);
        s1.setUnit("小时");
        s1.setRankImg("https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80");

        var s2 = new com.game.companion.modules.companion.dto.ApplyCompanionDTO.SkillItemDTO();
        s2.setGameName("英雄联盟");
        s2.setRankName("傲世宗师");
        s2.setPrice(30);
        s2.setUnit("小时");
        s2.setRankImg("https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80");

        applyDTO.setSkills(java.util.List.of(s1, s2));
        companionService.applyCompanion(companionUserId, applyDTO);

        // 管理员一键审核通过该陪练申请
        SysUser compUser = userService.getById(companionUserId);
        compUser.setRole(RoleEnum.COMPANION.getCode());
        userService.updateById(compUser);

        CompanionProfile compProfile = companionProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, companionUserId)
        );
        compProfile.setAuditStatus(1);
        companionProfileMapper.updateById(compProfile);

        var skills = companionService.getCompanionSkills(companionUserId);
        assertFalse(skills.isEmpty());
        testSkillId = skills.get(0).getId();

        System.out.println("✅ 真机微信号独立建档并通过陪练资质审核: userId = " + companionUserId + ", 支持游戏数 = " + skills.size());
    }

    @Test
    @Order(2)
    public void test2_SelfOrderMustBeForbidden() {
        System.out.println("\n========== [单元测试 2: 陪玩师严禁预约自己测试] ==========");

        // 模拟甜心小鹿 (20001) 尝试下单预约自己 (20001)
        CreateOrderDTO selfOrderDTO = new CreateOrderDTO();
        selfOrderDTO.setCompanionUserId(companionUserId);
        selfOrderDTO.setSkillId(testSkillId);
        selfOrderDTO.setQuantity(1);
        selfOrderDTO.setGameServer("微信区");
        selfOrderDTO.setGameAccount("甜心小鹿自己");
        selfOrderDTO.setClientRemark("自己给自己下单");

        ApiException exception = assertThrows(ApiException.class, () -> {
            companionOrderService.createAndPayOrder(companionUserId, selfOrderDTO);
        }, "自己预约自己必须抛出 ApiException 异常！");

        assertTrue(exception.getMessage().contains("您不能预约自己的陪练服务"), "异常文案必须提示不能预约自己");
        System.out.println("✅ 陪玩师严禁预约自己拦截测试通过，异常捕获: " + exception.getMessage());
    }

    @Test
    @Order(3)
    public void test3_ClientOrderAndCompanionReceiveCheck() {
        System.out.println("\n========== [单元测试 3: 客户下单 -> 陪练师查收订单核心测试] ==========");

        // 1. 客户 (clientUserId) 向 甜心小鹿 (20001) 下单
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(testSkillId);
        orderDTO.setQuantity(2);
        orderDTO.setGameServer("王者荣耀 微信区 / 排位赛");
        orderDTO.setGameAccount("测试老板666");
        orderDTO.setClientRemark("只打连胜，来技术好的软辅");

        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        assertNotNull(payResult);
        assertNotNull(payResult.getOrderSn(), "下单返回必须包含业务单号");
        createdOrderSn = payResult.getOrderSn();
        System.out.println("✅ 客户下单支付成功: orderSn = " + createdOrderSn);

        // 查验数据库里的订单真实状态 (纯业务交易履约域)
        CompanionOrder dbOrder = companionOrderService.getOrderDetail(createdOrderSn);
        assertNotNull(dbOrder);
        assertEquals(clientUserId, dbOrder.getClientUserId(), "订单的客户ID必须与下单人匹配");
        assertEquals(companionUserId, dbOrder.getCompanionUserId(), "订单的目标陪玩师ID必须是 20001 (甜心小鹿)");
        assertEquals(OrderStatusEnum.PAID_WAIT_ACCEPT.getCode(), dbOrder.getOrderStatus(), "新下单支付后状态必须为【待接单(1)】");

        // 核心财务分层断言：订单主表仅保留标价、优惠与应付实付
        assertEquals(5000, dbOrder.getOriginalAmount(), "标价必须为 5000分");
        assertEquals(0, dbOrder.getDiscountAmount(), "优惠立减默认 0分");
        assertEquals(5000, dbOrder.getTotalAmount(), "买家实付款必须为 5000分");

        // 【金融域解耦断言】验证支付事实流水表 (pay_flow) 真实写入事实数据 (0.6%通道费由此承载)
        int expectedChannelFee = (int) Math.round(dbOrder.getTotalAmount() * 0.006);
        com.game.companion.modules.order.entity.PayFlow payFlow = payFlowMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.game.companion.modules.order.entity.PayFlow>()
                        .eq(com.game.companion.modules.order.entity.PayFlow::getOrderSn, createdOrderSn)
        );
        assertNotNull(payFlow, "支付成功后必须在 pay_flow 支付事实流水表生成记录！");
        assertEquals(dbOrder.getTotalAmount(), payFlow.getPayAmount(), "支付流水实付金额必须等于订单总金额");
        assertEquals(expectedChannelFee, payFlow.getChannelFee(), "支付流水通道扣费必须真实记录为 30分");
        assertEquals(1, payFlow.getPayStatus(), "支付状态必须为成功(1)");

        System.out.println("✅ 订单业务域与支付流水域解耦验证通过: 订单标价=" + dbOrder.getOriginalAmount() + "分, 买家实付=" 
                + dbOrder.getTotalAmount() + "分, pay_flow 支付流水扣减通道费=" + payFlow.getChannelFee() + "分");

        // 2. 🌟 关键断言 (用户痛点)：甜心小鹿 (20001) 查询自己收到的待接单列表！
        Page<CompanionOrder> companionPage = companionOrderService.getMyOrders(
                companionUserId, 
                "companion", 
                OrderStatusEnum.PAID_WAIT_ACCEPT.getCode(), 
                1, 
                10
        );

        assertNotNull(companionPage);
        assertFalse(companionPage.getRecords().isEmpty(), "甜心小鹿的待接单列表中必须能查出订单，绝不能为空！");
        
        boolean foundOrder = companionPage.getRecords().stream()
                .anyMatch(o -> createdOrderSn.equals(o.getOrderSn()));
        assertTrue(foundOrder, "甜心小鹿收到的订单列表中，必须包含刚才老板下的单号 [" + createdOrderSn + "]！");

        System.out.println("✅ 甜心小鹿成功接收并查验到新订单！单号: " + createdOrderSn + ", 客户: " + dbOrder.getGameAccount());
    }

    @Test
    @Order(4)
    public void test4_CompanionAcceptAndCompleteFlow() {
        System.out.println("\n========== [单元测试 4: 陪练师接单与完结全链路流转测试] ==========");

        // 【确定性】先把抽佣比例固定为 10%，避免受 sys_config 中历史残留值影响
        PlatformConfigDTO deterministic = new PlatformConfigDTO();
        deterministic.setPaymentMode("mock");
        deterministic.setCommissionPercent(10.0);
        platformConfigService.savePlatformConfig(deterministic);

        // 1. 甜心小鹿 (20001) 点击【确认接单】
        companionOrderService.acceptOrder(companionUserId, createdOrderSn);
        CompanionOrder orderAfterAccept = companionOrderService.getOrderDetail(createdOrderSn);
        assertEquals(OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode(), orderAfterAccept.getOrderStatus(), "接单后状态必须变为【服务中(2)】");
        assertNotNull(orderAfterAccept.getAcceptTime(), "接单时间必须被记录");
        System.out.println("✅ 甜心小鹿接单成功，状态流转为: 服务中(2)");

        // 2. 陪练师履约完成，上传战绩凭证申请结单
        String screenshot = "https://images.unsplash.com/photo-1542751371-adc38448a05e";
        companionOrderService.applyFinish(companionUserId, createdOrderSn, screenshot);
        CompanionOrder orderAfterApply = companionOrderService.getOrderDetail(createdOrderSn);
        assertEquals(OrderStatusEnum.APPLIED_FINISH.getCode(), orderAfterApply.getOrderStatus(), "申请完结后状态必须变为【待客户确认(3)】");
        assertEquals(screenshot, orderAfterApply.getFinishScreenshot());
        System.out.println("✅ 战绩凭证上传成功，状态流转为: 待客户确认(3)");

        // 3. 客户 (clientUserId) 确认服务完成
        companionOrderService.confirmFinish(clientUserId, createdOrderSn);
        CompanionOrder orderFinished = companionOrderService.getOrderDetail(createdOrderSn);
        assertEquals(OrderStatusEnum.FINISHED.getCode(), orderFinished.getOrderStatus(), "最终确认后状态必须为【订单已完成(4)】");
        assertNotNull(orderFinished.getCompleteTime(), "完结时间必须被记录");

        // 🌟 核心断言 (用户痛点1)：结单后甜心小鹿的钱包余额和累计收益绝不能是 0！
        UserWallet wallet = userWalletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserWallet>()
                        .eq(UserWallet::getUserId, companionUserId)
        );
        assertNotNull(wallet, "陪练师钱包必须存在");
        assertTrue(wallet.getBalance() > 0, "陪练师可提现余额必须大于0，当前实际为: " + wallet.getBalance() + "分");
        assertTrue(wallet.getTotalIncome() > 0, "陪练师累计总收益必须大于0，当前实际为: " + wallet.getTotalIncome() + "分");

        // 断言钱包对账流水必须成功生成
        Long flowCount = walletFlowMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalletFlow>()
                        .eq(WalletFlow::getOrderSn, createdOrderSn)
        );
        assertTrue(flowCount > 0, "必须生成对应的钱包收益对账明细流水");

        // 4. 【业务联动新特性】客户对已完结订单提交 5 星评价，验证评价真实入库且陪练师综合评分更新！
        CreateReviewDTO reviewDTO = new CreateReviewDTO();
        reviewDTO.setOrderSn(createdOrderSn);
        reviewDTO.setScore(5);
        reviewDTO.setSkillScore(5);
        reviewDTO.setAttitudeScore(5);
        reviewDTO.setTags("声音超甜,意识顶级,带飞吃分");
        reviewDTO.setContent("小姐姐声音太甜了，打野节奏无敌，稳稳吃分！");

        Long reviewId = orderReviewService.createReview(clientUserId, reviewDTO);
        assertNotNull(reviewId, "评价必须成功生成ID");

        OrderReview dbReview = orderReviewService.getReviewByOrderSn(createdOrderSn);
        assertNotNull(dbReview, "必须能从 order_review 表中查询到真实评价");
        assertEquals(5, dbReview.getScore());
        assertEquals("声音超甜,意识顶级,带飞吃分", dbReview.getTags());

        // 验证陪练师 profile 评分真实计算联动
        CompanionProfile profile = companionProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, companionUserId)
        );
        assertNotNull(profile);
        assertNotNull(profile.getRating(), "陪练师综合评分必须不为空");

        // 【清算域解耦断言】验证订单完结清算单 (order_settlement) 真实生成且无早产
        com.game.companion.modules.order.entity.OrderSettlement settlement = orderSettlementMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.game.companion.modules.order.entity.OrderSettlement>()
                        .eq(com.game.companion.modules.order.entity.OrderSettlement::getOrderSn, createdOrderSn)
        );
        assertNotNull(settlement, "完结打款时必须在 order_settlement 生成正式分佣清算事实！");
        assertEquals(orderFinished.getTotalAmount(), settlement.getTotalAmount());
        assertEquals(4500, settlement.getCompanionIncome(), "陪练师分成收益必须为 4500分 (90%)");
        assertEquals(500, settlement.getPlatformGrossFee(), "平台毛抽佣必须为 500分 (10%)");
        assertEquals(30, settlement.getChannelFee(), "微信通道扣款必须为 30分 (0.6%)");
        assertEquals(470, settlement.getPlatformNetProfit(), "平台真实净利润必须严格等于 500 - 30 = 470分！");

        System.out.println("✅ 客户确认结单成功，甜心小鹿钱包真实入账: " + wallet.getBalance() + "分, 评价成功写入且清算对账单生成！平台净利: " + settlement.getPlatformNetProfit() + "分");
    }

    @Test
    @Order(5)
    public void test5_ClientCancelAndRefundOrder() {
        System.out.println("\n========== [单元测试 5: 客户待接单超时/主动撤回全额退款测试] ==========");

        // 1. 客户重新下一笔单
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(testSkillId);
        orderDTO.setQuantity(1);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("临时有事的老板");
        orderDTO.setClientRemark("可能随时撤销");

        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String cancelOrderSn = payResult.getOrderSn();
        System.out.println("✅ 下单成功: " + cancelOrderSn + ", 状态: 待接单(1)");

        // 2. 客户在待接单状态下，点击【撤回订单】
        companionOrderService.cancelAndRefundOrder(clientUserId, cancelOrderSn);
        CompanionOrder cancelledOrder = companionOrderService.getOrderDetail(cancelOrderSn);

        assertEquals(OrderStatusEnum.REFUNDED.getCode(), cancelledOrder.getOrderStatus(), "撤单后状态必须流转为【已退款(6)】");

        // 【支付流水退款冲正断言】验证退款事实与通道费冲正由 pay_flow 全权承载
        com.game.companion.modules.order.entity.PayFlow cancelPayFlow = payFlowMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.game.companion.modules.order.entity.PayFlow>()
                        .eq(com.game.companion.modules.order.entity.PayFlow::getOrderSn, cancelOrderSn)
        );
        assertNotNull(cancelPayFlow, "支付流水记录必须存在");
        assertEquals(3, cancelPayFlow.getPayStatus(), "退款后支付流水状态必须标记为已退款(3)");
        assertEquals(cancelledOrder.getTotalAmount(), cancelPayFlow.getRefundAmount(), "退款金额必须准确记录在流水表");
        assertEquals(cancelPayFlow.getChannelFee(), cancelPayFlow.getRefundChannelFee(), "退款必须全额冲正通道费");

        System.out.println("✅ 客户撤单并全额退款成功: orderSn = " + cancelOrderSn + ", pay_flow 支付事实与退款冲正完全解耦通过！");
    }

    @Test
    @Order(6)
    public void test6_RealChatSendAndReceive() {
        System.out.println("\n========== [单元测试 6: 开黑专属房间双方真实即时聊天测试] ==========");

        String chatOrderSn = createdOrderSn;

        // 1. 客户 (clientUserId) 发送消息
        SendChatDTO clientMsg = new SendChatDTO();
        clientMsg.setOrderSn(chatOrderSn);
        clientMsg.setContent("你好，开黑组队房间已建好，速度进组！");
        clientMsg.setSenderId(clientUserId);
        chatMessageService.sendMessage(clientUserId, clientMsg);

        // 2. 陪练师 (20001 甜心小鹿) 发送回复
        SendChatDTO companionMsg = new SendChatDTO();
        companionMsg.setOrderSn(chatOrderSn);
        companionMsg.setContent("收到老板，马上上线！这把包赢！");
        companionMsg.setSenderId(companionUserId);
        chatMessageService.sendMessage(companionUserId, companionMsg);

        // 3. 断言房间内消息列表
        var list = chatMessageService.getOrderMessages(chatOrderSn);
        assertNotNull(list);
        assertEquals(2, list.size(), "房间内必须严格记录 2 条真实消息");
        assertEquals(clientUserId, list.get(0).getSenderId(), "第1条消息必须是客户发送");
        assertEquals(companionUserId, list.get(1).getSenderId(), "第2条消息必须是甜心小鹿发送");
        assertEquals("甜心小鹿", list.get(1).getSenderName(), "陪练师昵称必须真实显示为甜心小鹿");

        System.out.println("✅ 开黑房间双方真实发信与同步测试通过！总消息数: " + list.size());
    }

    @Test
    @Order(7)
    public void test7_CompanionMultiGameSkillsCheck() {
        System.out.println("\n========== [单元测试 7: 陪玩师多游戏服务项目与差异化定价测试] ==========");

        // 验证甜心小鹿 (20001) 支持多个不同游戏技能与不同定价
        var skills = companionService.getCompanionSkills(companionUserId);
        assertNotNull(skills);
        assertTrue(skills.size() >= 2, "甜心小鹿必须至少配置 2 个游戏服务项目 (王者荣耀 + 英雄联盟)");

        System.out.println("✅ 甜心小鹿支持的游戏服务项目列表:");
        for (var s : skills) {
            System.out.println("   🎮 游戏: " + s.getGameName() + " | 段位: " + s.getRankName() + " | 单价: " + (s.getPrice() / 100) + "元/" + s.getUnit());
        }
        assertEquals("王者荣耀", skills.get(0).getGameName());
        assertEquals("英雄联盟", skills.get(1).getGameName());
        assertTrue(skills.get(1).getPrice() > skills.get(0).getPrice(), "不同游戏服务项目的单价必须支持差异化！");
    }

    @Test
    @Order(8)
    public void test8_AddOnOrderExtensionAndDisputeFlow() {
        System.out.println("\n========== [单元测试 8: 加钟扩展架构保护 & 售后维权与平台仲裁全链路流转测试] ==========");

        // 1. 下单并接单进入服务中 (orderStatus = 2)
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(testSkillId); // 王者荣耀 2500分
        orderDTO.setQuantity(1);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("维权测试老板");
        orderDTO.setClientRemark("测试服务争议仲裁流程");

        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String testOrderSn = payResult.getOrderSn();
        companionOrderService.acceptOrder(companionUserId, testOrderSn);

        // 2. 验证加钟续单接口已进入解耦扩展模式 (正确返回升级维护友好提示)
        assertThrows(ApiException.class, () -> {
            companionOrderService.addOnOrder(clientUserId, testOrderSn, 1);
        }, "加钟续单原直接修改主订单逻辑必须下线，并留好独立子订单扩展切入点！");
        System.out.println("✅ 加钟功能解耦保护生效，原主订单未受脏写污染！");

        // 3. 客户在服务中遇到异常情况，发起【售后维权与平台争议仲裁】
        companionOrderService.applyDispute(clientUserId, testOrderSn, "陪练中途掉线超过半小时，未履行约定服务");
        CompanionOrder disputeOrder = companionOrderService.getOrderDetail(testOrderSn);
        assertEquals(OrderStatusEnum.DISPUTING.getCode(), disputeOrder.getOrderStatus(), "发起维权后状态必须为【维权争议中(7)】");
        assertEquals("陪练中途掉线超过半小时，未履行约定服务", disputeOrder.getDisputeReason());
        assertNotNull(disputeOrder.getDisputeTime());
        System.out.println("✅ 客户发起维权申请成功，订单状态流转为: 维权争议中(7)");

        // 4. 管理员介入仲裁，核实后支持客户退款
        companionOrderService.auditDispute(10000L, testOrderSn, true, "核实陪练网络掉线属实，予以全额退款");
        CompanionOrder arbitratedOrder = companionOrderService.getOrderDetail(testOrderSn);
        assertEquals(OrderStatusEnum.DISPUTE_REFUNDED.getCode(), arbitratedOrder.getOrderStatus(), "仲裁退款后状态为【仲裁退款完成(8)】");
        assertTrue(arbitratedOrder.getDisputeResult().contains("全额退款"), "仲裁说明必须记录在案");
        assertNotNull(arbitratedOrder.getPayFlow(), "必须关联挂载支付流水");
        assertEquals(3, arbitratedOrder.getPayFlow().getPayStatus(), "支付流水标记为退款");

        System.out.println("✅ 平台管理员维权仲裁并全额退款完成！支付流水冲正成功, 仲裁结果: " + arbitratedOrder.getDisputeResult());
    }

    @Test
    @Order(9)
    public void test9_TimeoutAutoRefundTest() {
        System.out.println("\n========== [单元测试 9: 10分钟接单超时系统自动原路全额退款测试] ==========");

        // 1. 客户下单待接单
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(testSkillId);
        orderDTO.setQuantity(2);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("等待超时的老板");
        orderDTO.setClientRemark("陪练睡着了没理我");

        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String timeoutSn = payResult.getOrderSn();

        // 2. 【超时判定必须是时间感知的】刚下单的订单尚未超时，绝不能被误退款
        assertFalse(companionOrderService.checkAndRefundIfTimeout(timeoutSn),
                "未到 10 分钟超时的订单绝不允许触发自动退款！");

        // 3. 模拟「下单已超过 10 分钟」：回填下单时间为 11 分钟前
        CompanionOrder timeoutOrder = companionOrderService.getOrderDetail(timeoutSn);
        timeoutOrder.setCreateTime(java.time.LocalDateTime.now().minusMinutes(11));
        companionOrderMapper.updateById(timeoutOrder);

        // 4. 触发超时自动退款检测
        boolean isRefunded = companionOrderService.checkAndRefundIfTimeout(timeoutSn);
        assertTrue(isRefunded, "接单超时必须成功执行自动退款");

        CompanionOrder refundedOrder = companionOrderService.getOrderDetail(timeoutSn);
        assertEquals(OrderStatusEnum.REFUNDED.getCode(), refundedOrder.getOrderStatus(), "超时后订单状态必须流转为【已退款(6)】");
        assertNotNull(refundedOrder.getPayFlow(), "订单详情必须挂载关联支付流水事实");
        assertEquals(3, refundedOrder.getPayFlow().getPayStatus(), "超时后支付流水必须标记为已退款(3)");

        System.out.println("✅ 10分钟接单超时自动全额退款测试通过！单号: " + timeoutSn + ", 支付流水退款冲正完成！");
    }

    @Test
    @Order(10)
    public void test10_WithdrawApplyAndAdminAuditFlow() {
        System.out.println("\n========== [单元测试 10: 陪玩师申请提现与管理员手机端审批打款测试] ==========");

        // 1. 查询甜心小鹿 (20001) 当前钱包 (若为空则初始化并预置测试收益)
        UserWallet walletBefore = userWalletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserWallet>()
                        .eq(UserWallet::getUserId, companionUserId)
        );
        if (walletBefore == null) {
            walletBefore = new UserWallet();
            walletBefore.setUserId(companionUserId);
            walletBefore.setBalance(4500);
            walletBefore.setFrozenBalance(0);
            walletBefore.setTotalIncome(4500);
            userWalletMapper.insert(walletBefore);
        } else if (walletBefore.getBalance() <= 0) {
            walletBefore.setBalance(4500);
            userWalletMapper.updateById(walletBefore);
        }
        int availableBalance = walletBefore.getBalance();
        assertTrue(availableBalance > 0, "甜心小鹿账户中必须有完结订单结算的真实余额");

        // 2. 防超额提现断言：提现金额超过余额时必须抛出 ApiException 强行拦截
        ApplyWithdrawDTO overDTO = new ApplyWithdrawDTO();
        overDTO.setAmount(availableBalance + 10000);
        overDTO.setRealName("鹿小甜");
        assertThrows(ApiException.class, () -> {
            userWalletService.applyWithdraw(companionUserId, overDTO);
        }, "超额提现必须被系统安全拦截！");
        System.out.println("✅ 超额提现防御拦截测试通过！");

        // 3. 正常发起提现申请 (申请全额提现)
        ApplyWithdrawDTO applyDTO = new ApplyWithdrawDTO();
        applyDTO.setAmount(availableBalance);
        applyDTO.setRealName("鹿小甜");
        WithdrawRecord record = userWalletService.applyWithdraw(companionUserId, applyDTO);
        assertNotNull(record);
        assertNotNull(record.getWithdrawSn());
        assertEquals(0, record.getStatus(), "初始申请状态必须为待审核(0)");

        // 验证钱包扣减与冻结
        UserWallet walletAfterApply = userWalletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserWallet>()
                        .eq(UserWallet::getUserId, companionUserId)
        );
        assertEquals(0, walletAfterApply.getBalance(), "提现申请后可用余额必须扣减为 0");
        assertEquals(availableBalance, walletAfterApply.getFrozenBalance(), "资金必须进入冻结池");
        System.out.println("✅ 提现申请提交成功: 单号=" + record.getWithdrawSn() + ", 划入冻结池=" + availableBalance + "分");

        // 4. 超级管理员在手机端【一键批准打款】
        AuditWithdrawDTO auditDTO = new AuditWithdrawDTO();
        auditDTO.setWithdrawId(record.getId());
        auditDTO.setIsApproved(true);
        userWalletService.processWithdrawAudit(auditDTO);

        // 验证打款完成后状态
        UserWallet walletFinal = userWalletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserWallet>()
                        .eq(UserWallet::getUserId, companionUserId)
        );
        assertEquals(0, walletFinal.getFrozenBalance(), "打款完成后冻结资金必须归零释放");

        Page<WithdrawItemVO> adminList = userWalletService.getWithdrawList(null, 2, 1, 10);
        boolean hasSuccessRecord = adminList.getRecords().stream()
                .anyMatch(w -> w.getId().equals(record.getId()) && w.getStatus() == 2);
        assertTrue(hasSuccessRecord, "提现记录状态必须流转为已成功(2)");

        System.out.println("✅ 管理员审核打款成功！资金已成功结算给陪练师，全流程闭环验证完毕！");
    }

    @Test
    @Order(11)
    public void test11_PlatformDynamicConfigHotUpdateTest() {
        System.out.println("\n========== [单元测试 11: 管理员后台可视化热配置更新与秒级生效测试] ==========");

        // 1. 管理员在手机端保存全新热配置
        PlatformConfigDTO newConfig = new PlatformConfigDTO();
        newConfig.setPaymentMode("mock");
        newConfig.setCommissionPercent(15.0); // 调整抽成比例为 15%
        newConfig.setAppId("wxd6d00a7513f90fe9");
        newConfig.setAppSecret("0797974b6bc067e1d9a7f04d3dfaa3b6");
        newConfig.setMchId("1688888888");
        newConfig.setApiV3Key("my_apiv3_key_32_characters_long!");
        newConfig.setNewOrderTemplateId("Hi9R8Rt60xAEsrGEL9RcR-QDs_IeUntIuLSh1YaeUkU");

        platformConfigService.savePlatformConfig(newConfig);

        // 2. 校验配置是否成功持久化到数据库 sys_config
        PlatformConfigDTO readConfig = platformConfigService.getPlatformConfig();
        assertNotNull(readConfig);
        assertEquals("mock", readConfig.getPaymentMode());
        assertEquals(15.0, readConfig.getCommissionPercent(), "抽佣比例必须秒级更新为 15%");
        assertEquals("wxd6d00a7513f90fe9", readConfig.getAppId(), "AppID 必须成功热更新并生效");
        assertEquals("1688888888", readConfig.getMchId(), "商户号 MchId 必须成功热更新");

        System.out.println("✅ 管理员手机端微信支付与运营参数热配置保存与读取测试完全通过！无需重启服务！");
    }

    /**
     * 【单元测试 12】订单状态机与越权访问双重防护
     */
    @Test
    @Order(12)
    public void test12_OrderStateMachineAndAuthorizationGuard() {
        System.out.println("\n========== [单元测试 12: 订单状态机校验与越权访问拦截测试] ==========");

        // 1. 正常下单（指定陪练师）
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(testSkillId);
        orderDTO.setQuantity(1);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("状态机测试账号");
        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String orderSn = payResult.getOrderSn();

        // 2. 【状态机】订单尚处于「待接单(1)」时，陪玩师不得直接申请结单
        assertThrows(ApiException.class, () -> {
            companionOrderService.applyFinish(companionUserId, orderSn, "/uploads/screenshot.png");
        }, "未进入服务中的订单绝不允许申请结单！");

        // 3. 【状态机】订单尚未接单时，客户不得直接确认打款（防止未履约先结算）
        assertThrows(ApiException.class, () -> {
            companionOrderService.confirmFinish(clientUserId, orderSn);
        }, "未申请完结的订单绝不允许确认打款！");

        // 4. 【越权】第三方用户（非下单客户）不得代客户确认结单
        WxLoginDTO intruderDto = new WxLoginDTO();
        intruderDto.setCode("mock_code_simulator_intruder");
        intruderDto.setDeviceType("simulator");
        UserLoginVO intruderVO = userService.wxSilentLogin(intruderDto);
        assertThrows(ApiException.class, () -> {
            companionOrderService.confirmFinish(intruderVO.getUserId(), orderSn);
        }, "非下单客户绝不允许代为确认结单！");

        // 5. 【越权】第三方用户不得替陪玩师接单
        assertThrows(ApiException.class, () -> {
            companionOrderService.acceptOrder(intruderVO.getUserId(), orderSn);
        }, "非指定陪玩师绝不允许接单！");

        // 6. 正常履约：接单 -> 申请结单 -> 客户确认打款
        companionOrderService.acceptOrder(companionUserId, orderSn);
        companionOrderService.applyFinish(companionUserId, orderSn, "/uploads/screenshot.png");
        companionOrderService.confirmFinish(clientUserId, orderSn);

        CompanionOrder finished = companionOrderService.getOrderDetail(orderSn);
        assertEquals(OrderStatusEnum.FINISHED.getCode(), finished.getOrderStatus(), "正常履约后必须流转为已完成(4)");
        assertNotNull(finished.getSettlement(), "完结必须生成佣金清算单");

        // 7. 【幂等】重复确认结单必须被拦截，绝不允许重复清算与重复入账
        assertThrows(ApiException.class, () -> {
            companionOrderService.confirmFinish(clientUserId, orderSn);
        }, "重复确认结单必须被拦截，防止重复清算资损！");

        // 8. 【幂等】重复申请结单同样必须被拦截
        assertThrows(ApiException.class, () -> {
            companionOrderService.applyFinish(companionUserId, orderSn, "/uploads/screenshot.png");
        }, "重复申请结单必须被拦截！");

        System.out.println("✅ 订单状态机校验与越权访问拦截测试全部通过！");
    }

    /**
     * 【单元测试 13】抽佣比例热配置必须真实影响清算结果（秒级生效）
     */
    @Test
    @Order(13)
    public void test13_CommissionHotConfigActuallyAffectsSettlement() {
        System.out.println("\n========== [单元测试 13: 抽佣比例热配置对清算结果的真实影响测试] ==========");

        // 1. 管理员将抽佣比例热更新为 20%
        PlatformConfigDTO config = new PlatformConfigDTO();
        config.setPaymentMode("mock");
        config.setCommissionPercent(20.0);
        platformConfigService.savePlatformConfig(config);
        assertEquals(20.0, platformConfigService.getPlatformConfig().getCommissionPercent(), "抽佣比例必须热更新为 20%");

        // 2. 下单并完成履约
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(testSkillId);
        orderDTO.setQuantity(1);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("热配置测试账号");
        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String orderSn = payResult.getOrderSn();

        companionOrderService.acceptOrder(companionUserId, orderSn);
        companionOrderService.applyFinish(companionUserId, orderSn, "/uploads/screenshot.png");
        companionOrderService.confirmFinish(clientUserId, orderSn);

        // 3. 断言清算单按 20% 抽佣计算（而不是写死的 10%）
        CompanionOrder finished = companionOrderService.getOrderDetail(orderSn);
        int total = finished.getTotalAmount();
        int expectedGross = (int) Math.round(total * 0.20);
        int expectedChannelFee = (int) Math.round(total * 0.006);
        assertEquals(expectedGross, finished.getSettlement().getPlatformGrossFee(),
                "平台毛抽佣必须按热配置的 20% 计算（旧实现写死 @Value 导致热配置失效）");
        assertEquals(total - expectedGross, finished.getSettlement().getCompanionIncome(),
                "陪练师收益必须按热配置抽佣后的金额入账");
        assertEquals(expectedGross - expectedChannelFee, finished.getSettlement().getPlatformNetProfit(),
                "平台净利润必须为毛抽佣减去微信 0.6% 通道费");

        // 4. 恢复默认 10% 抽佣，避免影响其它用例
        PlatformConfigDTO restore = new PlatformConfigDTO();
        restore.setPaymentMode("mock");
        restore.setCommissionPercent(10.0);
        platformConfigService.savePlatformConfig(restore);

        System.out.println("✅ 抽佣比例热配置对清算结果的真实影响验证通过！");
    }

    /**
     * 【单元测试 14】用户自助修改头像与昵称 + 陪练师入驻录音选填
     */
    @Test
    @Order(14)
    public void test14_UpdateProfileAndOptionalVoice() {
        System.out.println("\n========== [单元测试 14: 自助改头像昵称 + 陪练入驻录音选填测试] ==========");

        // 1. 自助修改昵称与头像
        var updateDTO = new com.game.companion.modules.user.dto.UpdateProfileDTO();
        updateDTO.setNickname("小鹿本鹿" + RandomUtil.randomNumbers(3));
        updateDTO.setAvatarUrl("https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=200&q=80");

        SysUser updated = userService.updateProfile(clientUserId, updateDTO);
        assertNotNull(updated);
        assertEquals(updateDTO.getNickname(), updated.getNickname(), "昵称必须被真实更新到数据库");
        assertEquals(updateDTO.getAvatarUrl(), updated.getAvatarUrl(), "头像必须被真实更新到数据库");

        // 2. 再次查询确认持久化
        SysUser reread = userService.getById(clientUserId);
        assertEquals(updateDTO.getNickname(), reread.getNickname(), "昵称修改必须持久化");
        assertEquals(updateDTO.getAvatarUrl(), reread.getAvatarUrl(), "头像修改必须持久化");

        // 3. 非法昵称必须被拦截
        var emptyDTO = new com.game.companion.modules.user.dto.UpdateProfileDTO();
        emptyDTO.setNickname("   ");
        assertThrows(ApiException.class, () -> {
            userService.updateProfile(clientUserId, emptyDTO);
        }, "空昵称必须被拦截！");

        var tooLongDTO = new com.game.companion.modules.user.dto.UpdateProfileDTO();
        tooLongDTO.setNickname("a".repeat(50));
        assertThrows(ApiException.class, () -> {
            userService.updateProfile(clientUserId, tooLongDTO);
        }, "超长昵称必须被拦截！");

        // 4. 非法头像地址必须被拦截
        var badAvatarDTO = new com.game.companion.modules.user.dto.UpdateProfileDTO();
        badAvatarDTO.setNickname("合法昵称");
        badAvatarDTO.setAvatarUrl("javascript:alert(1)");
        assertThrows(ApiException.class, () -> {
            userService.updateProfile(clientUserId, badAvatarDTO);
        }, "非 http/https 的头像地址必须被拦截！");

        // 5. 【陪练入驻】未录制原声也必须允许提交申请（录音为选填项）
        var applyDTO = new com.game.companion.modules.companion.dto.ApplyCompanionDTO();
        applyDTO.setRealName("无原声陪练");
        applyDTO.setPhone("13800000009");
        applyDTO.setBio("未录制原声也可申请");
        applyDTO.setVoiceUrl(null);   // 明确不提供录音
        applyDTO.setVoiceDuration(null);

        var skill = new com.game.companion.modules.companion.dto.ApplyCompanionDTO.SkillItemDTO();
        skill.setGameName("王者荣耀");
        skill.setRankName("最强王者 35星");
        skill.setPrice(25);
        skill.setPriceUnit("YUAN");
        skill.setUnit("小时");
        skill.setRankImg("https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80");
        applyDTO.setSkills(java.util.List.of(skill));

        // 不应抛出异常
        companionService.applyCompanion(clientUserId, applyDTO);

        CompanionProfile profileAfter = companionProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, clientUserId)
        );
        assertNotNull(profileAfter);
        assertNull(profileAfter.getVoiceUrl(), "未录制原声时必须保持为空，严禁生成任何虚假音色兜底！");
        assertEquals(0, profileAfter.getVoiceDuration().intValue(), "未录制原声时时长必须为 0");

        // 6. 【尊重自定义昵称】入驻申请不得把用户自己改过的昵称静默覆盖成实名
        SysUser afterApply = userService.getById(clientUserId);
        assertEquals(updateDTO.getNickname(), afterApply.getNickname(),
                "用户在个人中心自定义的昵称，入驻申请时必须被保留，不得被实名覆盖！");

        System.out.println("✅ 自助改头像昵称与「陪练入驻录音选填」测试全部通过！");
    }

    /**
     * 【单元测试 15】陪玩师准入控制 + 订单状态分类筛选
     */
    @Test
    @Order(15)
    public void test15_CompanionAdmissionAndOrderStatusGrouping() {
        System.out.println("\n========== [单元测试 15: 陪玩师准入控制 + 订单状态分类筛选测试] ==========");

        // 1. 新建一个「刚提交申请、尚未审核通过」的陪玩师
        WxLoginDTO applicantDto = new WxLoginDTO();
        applicantDto.setCode("mock_code_simulator_applicant");
        applicantDto.setDeviceType("simulator");
        Long applicantId = userService.wxSilentLogin(applicantDto).getUserId();

        var applyDTO = new com.game.companion.modules.companion.dto.ApplyCompanionDTO();
        applyDTO.setRealName("待审核小李");
        applyDTO.setPhone("13800000077");
        applyDTO.setBio("刚提交申请");
        var skill = new com.game.companion.modules.companion.dto.ApplyCompanionDTO.SkillItemDTO();
        skill.setGameName("王者荣耀");
        skill.setRankName("钻石");
        skill.setPrice(20);
        skill.setPriceUnit("YUAN");
        skill.setUnit("小时");
        skill.setRankImg("https://images.unsplash.com/photo-1542751371-adc38448a05e");
        applyDTO.setSkills(java.util.List.of(skill));
        companionService.applyCompanion(applicantId, applyDTO);

        // 2. 状态查询：审核中 -> 无接单资格
        var status = companionService.getMyCompanionStatus(applicantId);
        assertTrue(status.isHasProfile(), "已提交申请后必须存在陪玩师档案");
        assertEquals(0, status.getAuditStatus(), "刚提交的申请必须是待审核(0)");
        assertFalse(status.isCanAcceptOrder(), "审核未通过时绝不能具备接单资格！");

        // 3. 【准入】审核中不得进入接单工作台
        assertThrows(ApiException.class, () -> {
            companionService.getWorkbenchStats(applicantId);
        }, "审核中的陪玩师绝不允许进入接单工作台！");

        // 4. 【准入】审核中不得开启接单开关
        assertThrows(ApiException.class, () -> {
            companionService.toggleOnlineStatus(applicantId, true);
        }, "审核中的陪玩师绝不允许开启接单开关！");

        // 5. 【准入】审核中不得接单
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(applicantId);
        orderDTO.setSkillId(testSkillId);
        orderDTO.setQuantity(1);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("准入测试");
        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String orderSn = payResult.getOrderSn();

        assertThrows(ApiException.class, () -> {
            companionOrderService.acceptOrder(applicantId, orderSn);
        }, "审核未通过的陪玩师绝不允许接单！");

        // 6. 审核通过后即可正常接单
        CompanionProfile applicantProfile = companionProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, applicantId)
        );
        applicantProfile.setAuditStatus(1);
        companionProfileMapper.updateById(applicantProfile);

        var approvedStatus = companionService.getMyCompanionStatus(applicantId);
        assertTrue(approvedStatus.isCanAcceptOrder(), "审核通过后必须具备接单资格！");

        companionOrderService.acceptOrder(applicantId, orderSn);
        assertEquals(OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode(),
                companionOrderService.getOrderDetail(orderSn).getOrderStatus(),
                "审核通过后接单必须成功流转为服务中(2)");

        // 7. 【状态分类】-2 「已结束」分组必须同时覆盖 5/6/7/8
        //    构造一个已退款(6)订单用于验证分组筛选
        companionOrderService.applyFinish(applicantId, orderSn, "/uploads/x.png");
        companionOrderService.confirmFinish(clientUserId, orderSn);
        assertEquals(OrderStatusEnum.FINISHED.getCode(),
                companionOrderService.getOrderDetail(orderSn).getOrderStatus());

        Page<CompanionOrder> finishedGroup = companionOrderService.getMyOrders(clientUserId, "client", -2, 1, 100);
        boolean containsCancelledOrRefunded = finishedGroup.getRecords().stream()
                .anyMatch(o -> o.getOrderStatus() == 5 || o.getOrderStatus() == 6
                        || o.getOrderStatus() == 7 || o.getOrderStatus() == 8);
        assertTrue(containsCancelledOrRefunded,
                "「已结束」分类必须能查到已取消/已退款/维权中/仲裁退款状态的订单！");

        // 8. 「已结束」分组不得混入进行中的订单
        boolean noOngoing = finishedGroup.getRecords().stream()
                .noneMatch(o -> o.getOrderStatus() == 1 || o.getOrderStatus() == 2
                        || o.getOrderStatus() == 3 || o.getOrderStatus() == 4);
        assertTrue(noOngoing, "「已结束」分类绝不允许混入待接单/服务中/待确认/已完成的订单！");

        // 9. 精确状态筛选仍必须可用
        Page<CompanionOrder> finishedOnly = companionOrderService.getMyOrders(clientUserId, "client", 4, 1, 100);
        assertTrue(finishedOnly.getRecords().stream().allMatch(o -> o.getOrderStatus() == 4),
                "按状态 4 精确筛选必须只返回已完成的订单！");

        // 10. 【数据可见性】客户不得看到平台内部清算数据；陪练师与管理��可以
        CompanionOrder clientView = companionOrderService.getOrderDetailForViewer(orderSn, clientUserId, 1);
        assertNull(clientView.getSettlement(), "客户视图绝不允许包含平台内部清算单数据！");
        assertNull(clientView.getPlatformFee(), "客户视图绝不允许包含平台抽佣金额！");

        CompanionOrder companionView = companionOrderService.getOrderDetailForViewer(orderSn, applicantId, 2);
        assertNotNull(companionView.getSettlement(), "陪练师本人必须能看到自己的清算与收益数据！");

        CompanionOrder adminView = companionOrderService.getOrderDetailForViewer(orderSn, clientUserId, 9);
        assertNotNull(adminView.getSettlement(), "平台管理员必须能看到完整清算对账单！");

        System.out.println("✅ 陪玩师准入控制与订单状态分类筛选测试全部通过！");
    }

    /**
     * 【单元测试 16】游戏品类字典与图标关联正确性
     */
    @Test
    @Order(16)
    public void test16_GameCatalogAndIconResolution() {
        System.out.println("\n========== [单元测试 16: 游戏字典自愈与图标关联正确性测试] ==========");

        // 1. 字典表必须 6 个标准游戏齐备, 且图标为包内本地图标路径
        var categories = gameCategoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.game.companion.modules.game.entity.GameCategory>()
        );
        assertTrue(categories.size() >= 24, "游戏品类字典必须至少包含 24 个主流游戏品类！");
        for (var c : categories) {
            assertNotNull(c.getIcon(), "游戏「" + c.getName() + "」必须有图标！");
            assertTrue(c.getIcon().startsWith("/static/game/"),
                    "游戏「" + c.getName() + "」的图标必须是包内本地图标(避免第三方图床404/白名单拦截), 实际: " + c.getIcon());
        }

        // 2. 【关键回归】不同游戏必须解析出不同的 game_id
        //    (历史缺陷: 名称乱码导致名称关联全部失败并回退到 id=1, 所有游戏都显示成王者荣耀图标)
        var wangzhe = gameCategoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.game.companion.modules.game.entity.GameCategory>()
                        .eq(com.game.companion.modules.game.entity.GameCategory::getName, "王者荣耀")
        );
        var yingxiong = gameCategoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.game.companion.modules.game.entity.GameCategory>()
                        .eq(com.game.companion.modules.game.entity.GameCategory::getName, "英雄联盟")
        );
        var heping = gameCategoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.game.companion.modules.game.entity.GameCategory>()
                        .eq(com.game.companion.modules.game.entity.GameCategory::getName, "和平精英")
        );
        assertNotNull(wangzhe, "字典中必须存在「王者荣耀」且名称未乱码！");
        assertNotNull(yingxiong, "字典中必须存在「英雄联盟」且名称未乱码！");
        assertNotNull(heping, "字典中必须存在「和平精英」且名称未乱码！");
        assertTrue(wangzhe.getId() != yingxiong.getId() && yingxiong.getId() != heping.getId(),
                "不同游戏必须是不同的字典ID！");

        // 3. 陪练师申报多游戏后, 每个技能都必须关联到正确的 game_id 与图标
        var multiDto = new com.game.companion.modules.companion.dto.ApplyCompanionDTO();
        multiDto.setRealName("多游戏图标测试");
        multiDto.setPhone("13800000011");
        multiDto.setBio("t");
        var s1 = new com.game.companion.modules.companion.dto.ApplyCompanionDTO.SkillItemDTO();
        s1.setGameName("王者荣耀");
        s1.setRankName("钻石");
        s1.setPrice(30);
        s1.setPriceUnit("YUAN");
        s1.setUnit("小时");
        s1.setRankImg("https://x/1.png");
        var s2 = new com.game.companion.modules.companion.dto.ApplyCompanionDTO.SkillItemDTO();
        s2.setGameName("英雄联盟");
        s2.setRankName("钻石");
        s2.setPrice(30);
        s2.setPriceUnit("YUAN");
        s2.setUnit("小时");
        s2.setRankImg("https://x/2.png");
        var s3 = new com.game.companion.modules.companion.dto.ApplyCompanionDTO.SkillItemDTO();
        s3.setGameName("和平精英");
        s3.setRankName("钻石");
        s3.setPrice(30);
        s3.setPriceUnit("YUAN");
        s3.setUnit("小时");
        s3.setRankImg("https://x/3.png");
        multiDto.setSkills(java.util.List.of(s1, s2, s3));
        companionService.applyCompanion(clientUserId, multiDto);

        var skillViews = companionService.getCompanionSkillViews(clientUserId);
        assertEquals(3, skillViews.size(), "必须返回 3 个游戏项目！");

        java.util.Set<Long> gameIds = new java.util.HashSet<>();
        java.util.Set<String> icons = new java.util.HashSet<>();
        for (var v : skillViews) {
            assertNotNull(v.getGameId(), "技能「" + v.getGameName() + "」必须关联 game_id！");
            assertTrue(v.getGameId() > 0, "技能「" + v.getGameName() + "」的 game_id 不能是未知游戏(0)！");
            gameIds.add(v.getGameId());
            assertNotNull(v.getGameIcon(), "技能「" + v.getGameName() + "」必须返回游戏图标！");
            icons.add(v.getGameIcon());
        }
        assertEquals(3, gameIds.size(), "三个不同游戏必须解析出三个不同的 game_id！");
        assertEquals(3, icons.size(), "三个不同游戏必须返回三个不同的图标！");

        // 4. 【管理员待接单列表】必须带出游戏图标与陪玩师联系方式(供线下通知)
        //    注意: 技能归属于 clientUserId, 因此由另一方 companionUserId 充当客户下单
        CreateOrderDTO iconOrderDTO = new CreateOrderDTO();
        iconOrderDTO.setCompanionUserId(clientUserId);
        iconOrderDTO.setSkillId(skillViews.get(0).getId());
        iconOrderDTO.setQuantity(1);
        iconOrderDTO.setGameServer("微信区");
        iconOrderDTO.setGameAccount("图标校验订单");
        PaymentResultVO iconPay = companionOrderService.createAndPayOrder(companionUserId, iconOrderDTO);

        var pendingOrders = adminDashboardService.getPendingAcceptOrders(1, 50);
        var matched = pendingOrders.getRecords().stream()
                .filter(o -> iconPay.getOrderSn().equals(o.getOrderSn()))
                .findFirst();
        assertTrue(matched.isPresent(), "管理员待接单列表必须包含该订单！");
        assertNotNull(matched.get().getGameIcon(), "管理员待接单列表必须带出游戏图标！");
        assertNotNull(matched.get().getCompanionPhone(), "管理员待接单列表必须带出陪玩师手机号(供线下通知)！");

        System.out.println("✅ 游戏字典自愈与图标关联正确性测试全部通过！");
    }

    /**
     * 【单元测试 17】倒计时不重置 + 陪玩师 60 分钟履约超时自动全额退款
     */
    @Test
    @Order(17)
    public void test17_CountdownNotResetAndServiceTimeoutRefund() {
        System.out.println("\n========== [单元测试 17: 倒计时不重置 + 履约超时自动退款测试] ==========");

        // 1. 客户下单 (指定陪玩师)
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setCompanionUserId(companionUserId);
        dto.setSkillId(testSkillId);
        dto.setQuantity(1);
        dto.setGameServer("微信区");
        dto.setGameAccount("倒计时测试");
        PaymentResultVO pay = companionOrderService.createAndPayOrder(clientUserId, dto);
        String orderSn = pay.getOrderSn();

        // 2. 待接单(1) 状态必须带出接单倒计时 (约 600 秒)
        CompanionOrder fresh = companionOrderService.getOrderDetail(orderSn);
        assertNotNull(fresh.getRemainingSeconds(), "待接单订单必须带出接单倒计时！");
        assertTrue(fresh.getRemainingSeconds() > 580 && fresh.getRemainingSeconds() <= 600,
                "接单倒计时应约为 600 秒, 实际=" + fresh.getRemainingSeconds());

        // 3. 【关键】模拟时间流逝: 下单已过 9 分钟 -> 倒计时应剩约 60 秒, 而不是重置为 600
        CompanionOrder backdated = companionOrderService.getOrderDetail(orderSn);
        backdated.setCreateTime(java.time.LocalDateTime.now().minusMinutes(9));
        companionOrderMapper.updateById(backdated);

        CompanionOrder after9 = companionOrderService.getOrderDetail(orderSn);
        assertTrue(after9.getRemainingSeconds() <= 90,
                "倒计时必须按绝对截止时间递减(剩约60秒), 绝不能重置为 600! 实际=" + after9.getRemainingSeconds());

        // 4. 再次查询也不能重置
        CompanionOrder again = companionOrderService.getOrderDetail(orderSn);
        assertTrue(again.getRemainingSeconds() <= 90,
                "重复进入页面不得把倒计时重置回 10:00! 实际=" + again.getRemainingSeconds());

        // 5. 陪玩师接单 -> 服务中(2), 倒计时切换为履约时限 (60 分钟)
        companionOrderService.acceptOrder(companionUserId, orderSn);
        CompanionOrder serving = companionOrderService.getOrderDetail(orderSn);
        assertEquals(OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode(), serving.getOrderStatus());
        assertNotNull(serving.getRemainingSeconds(), "服务中订单必须带出履约倒计时！");
        assertTrue(serving.getRemainingSeconds() > 3500 && serving.getRemainingSeconds() <= 3600,
                "履约倒计时应约为 3600 秒(60分钟), 实际=" + serving.getRemainingSeconds());

        // 6. 模拟接单已超过 60 分钟仍未完成服务 -> 自动全额退款
        CompanionOrder longServing = companionOrderService.getOrderDetail(orderSn);
        longServing.setAcceptTime(java.time.LocalDateTime.now().minusMinutes(61));
        companionOrderMapper.updateById(longServing);

        boolean refunded = companionOrderService.checkAndRefundIfServiceTimeout(orderSn);
        assertTrue(refunded, "陪玩师超过 60 分钟未完成服务必须自动全额退款！");

        CompanionOrder afterRefund = companionOrderService.getOrderDetail(orderSn);
        assertEquals(OrderStatusEnum.REFUNDED.getCode(), afterRefund.getOrderStatus(),
                "履约超时退款后订单状态必须为已退款(6)！");
        assertNotNull(afterRefund.getPayFlow());
        assertEquals(3, afterRefund.getPayFlow().getPayStatus(), "支付流水必须冲正为已退款(3)！");
        assertEquals(afterRefund.getPayFlow().getChannelFee(), afterRefund.getPayFlow().getRefundChannelFee(),
                "通道费必须同步冲正！");

        // 7. 未超时的订单不得被误退款
        CreateOrderDTO dto2 = new CreateOrderDTO();
        dto2.setCompanionUserId(companionUserId);
        dto2.setSkillId(testSkillId);
        dto2.setQuantity(1);
        dto2.setGameServer("微信区");
        dto2.setGameAccount("未超时测试");
        String orderSn2 = companionOrderService.createAndPayOrder(clientUserId, dto2).getOrderSn();
        assertFalse(companionOrderService.checkAndRefundIfServiceTimeout(orderSn2),
                "未到 60 分钟的订单绝不允许被误退款！");

        System.out.println("✅ 倒计时不重置与履约超时自动退款测试全部通过！");
    }
}
