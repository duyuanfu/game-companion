package com.game.companion.modules.order.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.companion.common.enums.OrderStatusEnum;
import com.game.companion.common.enums.RoleEnum;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.mapper.ChatMessageMapper;
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import com.game.companion.modules.companion.mapper.CompanionSkillMapper;
import com.game.companion.modules.notice.service.NoticeService;
import com.game.companion.modules.order.dto.CreateOrderDTO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.order.service.CompanionOrderService;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import com.game.companion.modules.wallet.entity.UserWallet;
import com.game.companion.modules.wallet.entity.WalletFlow;
import com.game.companion.modules.wallet.mapper.UserWalletMapper;
import com.game.companion.modules.wallet.mapper.WalletFlowMapper;
import com.game.companion.payment.PaymentService;
import com.game.companion.payment.PaymentStrategyContext;
import com.game.companion.payment.dto.CreatePaymentParam;
import com.game.companion.payment.dto.PaymentResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanionOrderServiceImpl extends ServiceImpl<CompanionOrderMapper, CompanionOrder> implements CompanionOrderService {

    private final PaymentStrategyContext paymentStrategyContext;
    private final NoticeService noticeService;
    private final CompanionOrderMapper companionOrderMapper;
    private final SysUserMapper sysUserMapper;
    private final CompanionProfileMapper companionProfileMapper;
    private final CompanionSkillMapper companionSkillMapper;
    private final UserWalletMapper userWalletMapper;
    private final WalletFlowMapper walletFlowMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Value("${platform.commission-rate:0.10}")
    private Double commissionRate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResultVO createAndPayOrder(Long clientUserId, CreateOrderDTO dto) {
        // 0. 【严密防线】严禁自己预约自己的陪练服务 (防刷单)
        if (dto.getCompanionUserId() != null && dto.getCompanionUserId().equals(clientUserId)) {
            throw new ApiException("操作被拒绝：您不能预约自己的陪练服务！");
        }

        // 1. 生成全局唯一订单号 (年月日时分秒 + 随机6位)
        String orderSn = DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
        
        // 2. 真实从数据库 companion_skill 读取该技能项目真实单价与游戏名称
        CompanionSkill skill = companionSkillMapper.selectById(dto.getSkillId());
        int unitPrice = (skill != null && skill.getPrice() > 0) ? skill.getPrice() : 2500;
        String gameName = (skill != null && skill.getGameName() != null) ? skill.getGameName() : "开黑电竞";

        int totalAmount = unitPrice * dto.getQuantity();
        int platformFee = (int) Math.round(totalAmount * commissionRate);
        int companionIncome = totalAmount - platformFee;

        // 3. 组装订单实体
        CompanionOrder order = new CompanionOrder();
        order.setOrderSn(orderSn);
        order.setClientUserId(clientUserId);
        order.setCompanionUserId(dto.getCompanionUserId());
        order.setOriginalCompanionId(dto.getCompanionUserId());
        order.setSkillId(dto.getSkillId());
        order.setGameName(gameName);
        order.setUnitPrice(unitPrice);
        order.setQuantity(dto.getQuantity());
        order.setTotalAmount(totalAmount);
        order.setPlatformFee(platformFee);
        order.setCompanionIncome(companionIncome);
        order.setOrderType(dto.getCompanionUserId() != null ? 1 : 2);
        order.setIsTransferred(0);
        order.setGameServer(dto.getGameServer());
        order.setGameAccount(dto.getGameAccount());
        order.setClientRemark(dto.getClientRemark());
        order.setOrderStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setPayStatus(0);
        order.setCreateTime(LocalDateTime.now());

        save(order);

        // 3. 调用抽象支付网关
        PaymentService paymentService = paymentStrategyContext.getService();
        CreatePaymentParam paymentParam = CreatePaymentParam.builder()
                .orderSn(orderSn)
                .amount(totalAmount)
                .description("游戏开黑服务 - " + order.getGameName())
                .clientOpenid("mock_openid")
                .build();

        PaymentResultVO paymentResult = paymentService.createPayment(paymentParam);

        // 4. 若为 Mock 模式，自动模拟完成支付并流转为“待接单”同时发微信通知
        if (Boolean.TRUE.equals(paymentResult.getIsMockPaid())) {
            mockPaySuccess(orderSn);
        }

        return paymentResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mockPaySuccess(String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("订单不存在: " + orderSn);
        }

        order.setPayStatus(1);
        order.setPayType("mock");
        order.setTransactionId("mock_tx_" + IdUtil.fastSimpleUUID());
        order.setPayTime(LocalDateTime.now());
        order.setOrderStatus(OrderStatusEnum.PAID_WAIT_ACCEPT.getCode());
        updateById(order);

        // 触发微信强提醒通知陪玩师
        if (order.getCompanionUserId() != null) {
            noticeService.sendNewOrderNotice(order.getCompanionUserId(), orderSn, order.getGameName(), order.getTotalAmount(), order.getClientRemark());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOrder(Long companionUserId, String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("订单不存在");
        }
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.PAID_WAIT_ACCEPT.getCode()) {
            throw new ApiException("订单状态不是待接单状态");
        }
        if (!companionUserId.equals(order.getCompanionUserId())) {
            throw new ApiException("非指定接单陪玩师，无法接单");
        }

        order.setOrderStatus(OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode());
        order.setAcceptTime(LocalDateTime.now());
        updateById(order);

        log.info("【陪玩师已接单】订单号={}, 陪玩师ID={}, 激活双方IM沟通房间", orderSn, companionUserId);
    }

    @Override
    public void grabOrderFromHall(Long companionUserId, String orderSn) {
        // 1. 【后端安全铁律防线】校验抢单人身份：必须是认证陪玩师 (role = 2)
        SysUser user = sysUserMapper.selectById(companionUserId);
        if (user == null || user.getRole() == null || user.getRole() != RoleEnum.COMPANION.getCode()) {
            throw new ApiException("您当前是普通点单客户，尚未认证成为陪玩师，无法参与接单！");
        }

        // 2. 校验陪玩师审核状态与接单状态
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, companionUserId)
        );
        if (profile == null || profile.getAuditStatus() != 1) {
            throw new ApiException("您的陪玩师入驻资质尚未通过审核，暂无法接单");
        }
        if (profile.getIsOnline() != 1) {
            throw new ApiException("您当前处于休息勿扰状态，请先在工作台开启接单开关！");
        }

        // 3. 校验订单归属：严禁自己抢自己的订单 (防虚假刷单)
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("订单不存在");
        }
        if (companionUserId.equals(order.getClientUserId())) {
            throw new ApiException("操作被拒绝：您不能接自己发布的点单需求！");
        }

        // 4. 标准生产级：Redisson 分布式锁防超卖
        String lockKey = "lock:order:grab:" + orderSn;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(2, 5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ApiException("抢单人数过多，请重试");
            }

            int rows = companionOrderMapper.grabOrderAtomically(orderSn, companionUserId);
            if (rows <= 0) {
                throw new ApiException("手慢了，该单已被其他陪玩师抢走！");
            }
            log.info("【抢单成功】认证陪玩师[{}]成功抢下订单[{}]", companionUserId, orderSn);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("抢单失败");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferToPublicHall(String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order != null && order.getOrderStatus() != null && order.getOrderStatus() == OrderStatusEnum.PAID_WAIT_ACCEPT.getCode()) {
            order.setIsTransferred(1);
            order.setOrderType(2); // 流转为大厅抢单
            order.setCompanionUserId(null); // 释放指定绑定
            updateById(order);
            log.info("【超时自动流转】订单[{}]指定陪玩师超时未响应，已顺利流转至派单大厅！", orderSn);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyFinish(Long companionUserId, String orderSn, String screenshotUrl) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null || !companionUserId.equals(order.getCompanionUserId())) {
            throw new ApiException("无权操作此订单");
        }
        order.setOrderStatus(OrderStatusEnum.APPLIED_FINISH.getCode());
        order.setFinishScreenshot(screenshotUrl);
        updateById(order);
        log.info("【陪玩师申请结单】订单号={}, 凭证={}", orderSn, screenshotUrl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmFinish(Long clientUserId, String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null || !clientUserId.equals(order.getClientUserId())) {
            throw new ApiException("无权操作此订单");
        }
        order.setOrderStatus(OrderStatusEnum.FINISHED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        order.setSettleTime(LocalDateTime.now());
        updateById(order);

        // 1. 真实将收益划入陪玩师钱包 (user_wallet)
        Long companionId = order.getCompanionUserId();
        int income = order.getCompanionIncome();

        UserWallet wallet = userWalletMapper.selectOne(
                new LambdaQueryWrapper<UserWallet>().eq(UserWallet::getUserId, companionId)
        );
        if (wallet == null) {
            wallet = new UserWallet();
            wallet.setUserId(companionId);
            wallet.setBalance(0);
            wallet.setFrozenBalance(0);
            wallet.setTotalIncome(0);
            wallet.setCreateTime(LocalDateTime.now());
            userWalletMapper.insert(wallet);
        }

        int beforeBalance = wallet.getBalance();
        wallet.setBalance(beforeBalance + income);
        wallet.setTotalIncome(wallet.getTotalIncome() + income);
        wallet.setUpdateTime(LocalDateTime.now());
        userWalletMapper.updateById(wallet);

        // 2. 真实记录钱包明细流水 (wallet_flow)
        WalletFlow flow = new WalletFlow();
        flow.setFlowSn("FLOW_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4));
        flow.setUserId(companionId);
        flow.setType(1); // 1-订单收益结算
        flow.setAmount(income);
        flow.setBeforeBalance(beforeBalance);
        flow.setAfterBalance(wallet.getBalance());
        flow.setOrderSn(order.getOrderSn());
        flow.setRemark("陪练订单完结收益入账 - " + order.getGameName());
        flow.setCreateTime(LocalDateTime.now());
        walletFlowMapper.insert(flow);

        // 3. 陪玩师个人主档的已接单总量累加 +1
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, companionId)
        );
        if (profile != null) {
            profile.setOrderCount(profile.getOrderCount() + 1);
            companionProfileMapper.updateById(profile);
        }

        log.info("【订单圆满完成】划扣收益 {}分 入陪玩师[{}]钱包, 钱包最新余额: {}分", income, companionId, wallet.getBalance());
    }

    @Override
    public Page<CompanionOrder> getHallOrders(int pageNum, int pageSize) {
        Page<CompanionOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<CompanionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompanionOrder::getOrderStatus, OrderStatusEnum.PAID_WAIT_ACCEPT.getCode())
               .eq(CompanionOrder::getOrderType, 2)
               .orderByDesc(CompanionOrder::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public CompanionOrder getOrderDetail(String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("未找到该订单: " + orderSn);
        }
        return order;
    }

    @Override
    public Page<CompanionOrder> getMyOrders(Long userId, String roleType, Integer orderStatus, int pageNum, int pageSize) {
        Page<CompanionOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<CompanionOrder> wrapper = new LambdaQueryWrapper<>();
        
        if ("companion".equalsIgnoreCase(roleType)) {
            wrapper.eq(CompanionOrder::getCompanionUserId, userId);
        } else {
            wrapper.eq(CompanionOrder::getClientUserId, userId);
        }

        if (orderStatus != null && orderStatus >= 0) {
            wrapper.eq(CompanionOrder::getOrderStatus, orderStatus);
        }

        wrapper.orderByDesc(CompanionOrder::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAndRefundOrder(Long clientUserId, String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("未找到该订单");
        }
        if (!clientUserId.equals(order.getClientUserId())) {
            throw new ApiException("操作被拒绝：您只能撤回自己下的订单");
        }
        // 只有待接单状态(1)允许客户自主无条件全额撤回退款
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.PAID_WAIT_ACCEPT.getCode()) {
            throw new ApiException("陪练师已接单或订单已完结，无法直接撤回，如需退款请联系客服协商！");
        }

        // 调用退款网关 (Mock / 微信V3)
        PaymentService paymentService = paymentStrategyContext.getService();
        boolean refundSuccess = paymentService.refund(orderSn, order.getTotalAmount(), order.getTotalAmount(), "客户待接单超时或主动撤回");
        if (!refundSuccess) {
            throw new ApiException("退款处理失败，请稍后重试");
        }

        order.setOrderStatus(OrderStatusEnum.REFUNDED.getCode()); // 6 已退款
        order.setPayStatus(2); // 2 已退款
        updateById(order);

        log.info("【客户主动撤单并退款成功】订单号={}, 客户ID={}, 全额退款={}分", orderSn, clientUserId, order.getTotalAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResultVO addOnOrder(Long clientUserId, String orderSn, int addQuantity) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("未找到该订单");
        }
        if (!clientUserId.equals(order.getClientUserId())) {
            throw new ApiException("操作被拒绝：只有下单客户本人可以发起加钟续单");
        }
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode()) {
            throw new ApiException("当前订单未处于服务沟通中，无法加钟续单！");
        }
        if (addQuantity <= 0) {
            throw new ApiException("加钟数量至少为 1");
        }

        // 1. 计算加钟费用与提成
        int addAmount = order.getUnitPrice() * addQuantity;
        int addFee = (int) Math.round(addAmount * commissionRate);
        int addIncome = addAmount - addFee;

        // 2. 模拟/真实支付加钟款项
        PaymentService paymentService = paymentStrategyContext.getService();
        CreatePaymentParam paymentParam = CreatePaymentParam.builder()
                .orderSn(orderSn + "_ADD_" + addQuantity)
                .amount(addAmount)
                .description("开黑加钟续单 - " + order.getGameName() + " (+" + addQuantity + ")")
                .clientOpenid("mock_openid")
                .build();
        PaymentResultVO payResult = paymentService.createPayment(paymentParam);

        // 3. 更新主订单数量与总额
        order.setQuantity(order.getQuantity() + addQuantity);
        order.setTotalAmount(order.getTotalAmount() + addAmount);
        order.setPlatformFee(order.getPlatformFee() + addFee);
        order.setCompanionIncome(order.getCompanionIncome() + addIncome);
        updateById(order);

        // 4. 自动向开黑聊天房间插入一条系统加钟广播
        ChatMessage sysMsg = new ChatMessage();
        sysMsg.setOrderSn(orderSn);
        sysMsg.setSenderId(0L); // 0 代表系统消息
        sysMsg.setSenderName("平台系统广播");
        sysMsg.setSenderAvatar("https://img.icons8.com/color/96/lightning-bolt.png");
        sysMsg.setContent("⚡️ 老板刚刚成功加钟续费 " + addQuantity + " 局/小时！当前累计预约时长: " + order.getQuantity() + "，继续带飞开黑！");
        sysMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(sysMsg);

        log.info("【客户加钟续单成功】订单号={}, 增加数量={}, 新总额={}分, 陪练师新收益={}分",
                orderSn, addQuantity, order.getTotalAmount(), order.getCompanionIncome());

        payResult.setOrderSn(orderSn);
        return payResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndRefundIfTimeout(String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) return false;

        // 只有待接单状态(1)才检测超时
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.PAID_WAIT_ACCEPT.getCode()) {
            return false;
        }

        // 生产规则：超过 10 分钟自动退款
        // 触发自动原路退款
        cancelAndRefundOrder(order.getClientUserId(), orderSn);
        log.info("【接单超时自动退款触发】订单[{}]陪练师超时未接单，已自动为客户[{}]原路退款！", orderSn, order.getClientUserId());
        return true;
    }
}
