package com.game.companion;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    private static Long clientUserId;
    private static Long companionUserId = 20001L; // 甜心小鹿固定ID
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

        // 2. 测试真机端 (手机微信) -> 必须被识别为【陪玩师 甜心小鹿 (role = 2, ID = 20001)】
        WxLoginDTO phoneDto = new WxLoginDTO();
        phoneDto.setCode("mock_code_phone_companion");
        phoneDto.setDeviceType("phone");
        UserLoginVO companionVO = userService.wxSilentLogin(phoneDto);

        assertNotNull(companionVO);
        assertEquals(20001L, companionVO.getUserId(), "真机端必须绑定甜心小鹿(ID=20001)");
        assertEquals(RoleEnum.COMPANION.getCode(), companionVO.getRole(), "真机端角色必须是陪玩师(role=2)");
        assertEquals("甜心小鹿", companionVO.getNickname(), "真机端昵称必须是甜心小鹿");
        System.out.println("✅ 真机陪练师登录测试通过: userId = " + companionVO.getUserId() + ", 昵称 = " + companionVO.getNickname());
    }

    @Test
    @Order(2)
    public void test2_SelfOrderMustBeForbidden() {
        System.out.println("\n========== [单元测试 2: 陪玩师严禁预约自己测试] ==========");

        // 模拟甜心小鹿 (20001) 尝试下单预约自己 (20001)
        CreateOrderDTO selfOrderDTO = new CreateOrderDTO();
        selfOrderDTO.setCompanionUserId(companionUserId);
        selfOrderDTO.setSkillId(1L);
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
        orderDTO.setSkillId(1L);
        orderDTO.setQuantity(2);
        orderDTO.setGameServer("王者荣耀 微信区 / 排位赛");
        orderDTO.setGameAccount("测试老板666");
        orderDTO.setClientRemark("只打连胜，来技术好的软辅");

        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        assertNotNull(payResult);
        assertNotNull(payResult.getOrderSn(), "下单返回必须包含业务单号");
        createdOrderSn = payResult.getOrderSn();
        System.out.println("✅ 客户下单支付成功: orderSn = " + createdOrderSn);

        // 查验数据库里的订单真实状态
        CompanionOrder dbOrder = companionOrderService.getOrderDetail(createdOrderSn);
        assertNotNull(dbOrder);
        assertEquals(clientUserId, dbOrder.getClientUserId(), "订单的客户ID必须与下单人匹配");
        assertEquals(companionUserId, dbOrder.getCompanionUserId(), "订单的目标陪玩师ID必须是 20001 (甜心小鹿)");
        assertEquals(OrderStatusEnum.PAID_WAIT_ACCEPT.getCode(), dbOrder.getOrderStatus(), "新下单支付后状态必须为【待接单(1)】");

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

        System.out.println("✅ 客户确认结单成功，甜心小鹿钱包真实入账: " + wallet.getBalance() + "分, 流水记录生成完毕！");
    }

    @Test
    @Order(5)
    public void test5_ClientCancelAndRefundOrder() {
        System.out.println("\n========== [单元测试 5: 客户待接单超时/主动撤回全额退款测试] ==========");

        // 1. 客户重新下一笔单
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(1L);
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
        assertEquals(2, cancelledOrder.getPayStatus(), "支付状态必须标记为【已退款(2)】");
        System.out.println("✅ 客户撤单并全额退款成功: orderSn = " + cancelOrderSn + ", 状态: 已退款(6)");
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
    public void test8_AddOnOrderTest() {
        System.out.println("\n========== [单元测试 8: 开黑聊天室一键加钟续单测试] ==========");

        // 1. 下单并接单进入服务中 (orderStatus = 2)
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(1L); // 王者荣耀 2500分
        orderDTO.setQuantity(1);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("加钟老板");
        orderDTO.setClientRemark("先打一局，打爽了加钟");

        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String addOrderSn = payResult.getOrderSn();
        companionOrderService.acceptOrder(companionUserId, addOrderSn);

        CompanionOrder beforeOrder = companionOrderService.getOrderDetail(addOrderSn);
        assertEquals(1, beforeOrder.getQuantity());
        assertEquals(2500, beforeOrder.getTotalAmount());

        // 2. 客户在聊天室点击【一键加钟 +1小时】
        PaymentResultVO addOnResult = companionOrderService.addOnOrder(clientUserId, addOrderSn, 1);
        assertNotNull(addOnResult);

        CompanionOrder afterOrder = companionOrderService.getOrderDetail(addOrderSn);
        assertEquals(2, afterOrder.getQuantity(), "加钟后预约数量必须累加为 2");
        assertEquals(5000, afterOrder.getTotalAmount(), "加钟后总金额必须更新为 5000分 (50.00元)");
        assertEquals(4500, afterOrder.getCompanionIncome(), "加钟后陪练师结算收入必须更新为 4500分 (45.00元)");

        // 3. 验证聊天室是否自动广播了系统加钟消息
        var chatList = chatMessageService.getOrderMessages(addOrderSn);
        assertFalse(chatList.isEmpty(), "聊天室内必须包含系统加钟广播消息");
        assertTrue(chatList.get(0).getContent().contains("老板刚刚成功加钟续费"), "消息正文必须包含加钟广播提示");

        System.out.println("✅ 一键加钟测试通过: 原单价25元(1小时) -> 加钟1小时 -> 累计50元(2小时)，系统广播入库成功！");
    }

    @Test
    @Order(9)
    public void test9_TimeoutAutoRefundTest() {
        System.out.println("\n========== [单元测试 9: 10分钟接单超时系统自动原路全额退款测试] ==========");

        // 1. 客户下单待接单
        CreateOrderDTO orderDTO = new CreateOrderDTO();
        orderDTO.setCompanionUserId(companionUserId);
        orderDTO.setSkillId(1L);
        orderDTO.setQuantity(2);
        orderDTO.setGameServer("微信区");
        orderDTO.setGameAccount("等待超时的老板");
        orderDTO.setClientRemark("陪练睡着了没理我");

        PaymentResultVO payResult = companionOrderService.createAndPayOrder(clientUserId, orderDTO);
        String timeoutSn = payResult.getOrderSn();

        // 2. 触发超时自动退款检测
        boolean isRefunded = companionOrderService.checkAndRefundIfTimeout(timeoutSn);
        assertTrue(isRefunded, "接单超时必须成功执行自动退款");

        CompanionOrder refundedOrder = companionOrderService.getOrderDetail(timeoutSn);
        assertEquals(OrderStatusEnum.REFUNDED.getCode(), refundedOrder.getOrderStatus(), "超时后订单状态必须流转为【已退款(6)】");
        assertEquals(2, refundedOrder.getPayStatus(), "支付状态必须标记为【已退款(2)】");

        System.out.println("✅ 10分钟接单超时自动全额退款测试通过！单号: " + timeoutSn + ", 退款状态: 已退款(6)");
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
}
