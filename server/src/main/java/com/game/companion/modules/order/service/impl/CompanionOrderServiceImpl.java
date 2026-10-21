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
import com.game.companion.modules.admin.service.PlatformConfigService;
import com.game.companion.modules.chat.entity.ChatMessage;
import com.game.companion.modules.chat.mapper.ChatMessageMapper;
import com.game.companion.modules.companion.entity.CompanionProfile;
import com.game.companion.modules.companion.entity.CompanionSkill;
import com.game.companion.modules.companion.mapper.CompanionProfileMapper;
import com.game.companion.modules.companion.mapper.CompanionSkillMapper;
import com.game.companion.modules.notice.service.NoticeService;
import com.game.companion.modules.order.dto.CreateOrderDTO;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.entity.OrderSettlement;
import com.game.companion.modules.order.entity.PayFlow;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.order.mapper.OrderSettlementMapper;
import com.game.companion.modules.order.mapper.PayFlowMapper;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanionOrderServiceImpl extends ServiceImpl<CompanionOrderMapper, CompanionOrder> implements CompanionOrderService {

    /**
     * 【列表筛选】「已结束」分组：已取消(5) / 已退款(6) / 维权争议中(7) / 仲裁退款完成(8)
     */
    public static final int ORDER_STATUS_FINISHED_GROUP = -2;

    private final PaymentStrategyContext paymentStrategyContext;
    private final NoticeService noticeService;
    private final CompanionOrderMapper companionOrderMapper;
    private final SysUserMapper sysUserMapper;
    private final CompanionProfileMapper companionProfileMapper;
    private final CompanionSkillMapper companionSkillMapper;
    private final UserWalletMapper userWalletMapper;
    private final WalletFlowMapper walletFlowMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final PayFlowMapper payFlowMapper;
    private final OrderSettlementMapper orderSettlementMapper;
    private final PlatformConfigService platformConfigService;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    /**
     * 平台抽佣比例兜底值 (0.10 = 10%)。
     * 实际生效值以管理员热配置 {@code platform.commission-percent} 为准，秒级生效。
     */
    @Value("${platform.commission-rate:0.10}")
    private Double commissionRate;

    /**
     * 微信支付通道费率 (0.6%)，落盘于 pay_flow.channel_fee 与 order_settlement.channel_fee。
     *
     * <p><b>重要说明</b>：微信支付官方并未提供「查询单笔交易实际通道费」的接口，
     * 0.6% 是商户与微信签约的生活服务类目费率，由微信在<b>结算时从商户号可结算资金中实时截留</b>，
     * 不会随支付回调一起返回。因此这里的数值是<b>按合同费率计算的预估值</b>，
     * 最终必须以微信「交易账单 / 对账文件」为准做月度对账。</p>
     *
     * <p>该值支持热配置（{@code platform.channel-fee-rate}），商户拿到实际费率合同后
     * 可在管理员热配置中心直接调整，无需重启服务。</p>
     */
    @Value("${platform.channel-fee-rate:0.006}")
    private Double channelFeeRate;

    /**
     * 指定单超时流转 / 自动退款阈值 (分钟)。
     */
    @Value("${platform.order-accept-timeout-minutes:10}")
    private int acceptTimeoutMinutes;

    /**
     * 陪玩师接单后的履约时长上限 (分钟)。超时未完成服务，系统自动为客户全额原路退款。
     */
    @Value("${platform.order-service-timeout-minutes:60}")
    private int serviceTimeoutMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResultVO createAndPayOrder(Long clientUserId, CreateOrderDTO dto) {
        if (clientUserId == null || clientUserId <= 0) {
            throw new ApiException("操作被拒绝：请先登录后再下单");
        }

        // 0. 【严密防线】严禁自己预约自己的陪练服务 (防刷单)
        if (dto.getCompanionUserId() != null && dto.getCompanionUserId().equals(clientUserId)) {
            throw new ApiException("操作被拒绝：您不能预约自己的陪练服务！");
        }

        // 1. 生成全局唯一订单号 (年月日时分秒 + 随机6位)
        String orderSn = DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);

        // 2. 真实从数据库 companion_skill 读取该技能项目真实单价与游戏名称
        CompanionSkill skill = companionSkillMapper.selectById(dto.getSkillId());
        if (skill == null) {
            throw new ApiException("操作被拒绝：所选服务项目不存在或已下架！");
        }
        int unitPrice = (skill.getPrice() != null && skill.getPrice() > 0) ? skill.getPrice() : 2500;
        String gameName = (skill.getGameName() != null) ? skill.getGameName() : "开黑电竞";

        int quantity = (dto.getQuantity() != null && dto.getQuantity() > 0) ? dto.getQuantity() : 1;
        int originalAmount = unitPrice * quantity;
        int discountAmount = 0; // 预留后续优惠券/满减营销扩展
        int totalAmount = originalAmount - discountAmount;

        // 3. 组装纯业务交易订单实体 (彻底解耦资金流水与分佣清算)
        CompanionOrder order = new CompanionOrder();
        order.setOrderSn(orderSn);
        order.setClientUserId(clientUserId);
        order.setCompanionUserId(dto.getCompanionUserId());
        order.setOriginalCompanionId(dto.getCompanionUserId());
        order.setSkillId(dto.getSkillId());
        order.setGameName(gameName);
        order.setUnitPrice(unitPrice);
        order.setQuantity(quantity);
        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);
        order.setOrderType(dto.getCompanionUserId() != null ? 1 : 2);
        order.setIsTransferred(0);
        order.setGameServer(dto.getGameServer());
        order.setGameAccount(dto.getGameAccount());
        order.setClientRemark(dto.getClientRemark());
        order.setOrderStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setCreateTime(LocalDateTime.now());

        save(order);

        // 4. 调用抽象支付网关 (真实模式下使用下单客户本人的真实 OpenID)
        PaymentService paymentService = paymentStrategyContext.getService();
        SysUser client = sysUserMapper.selectById(clientUserId);
        String clientOpenid = (client != null && client.getOpenid() != null && !client.getOpenid().isEmpty())
                ? client.getOpenid() : "mock_openid";
        CreatePaymentParam paymentParam = CreatePaymentParam.builder()
                .orderSn(orderSn)
                .amount(totalAmount)
                .description("游戏开黑服务 - " + order.getGameName())
                .clientOpenid(clientOpenid)
                .build();

        PaymentResultVO paymentResult = paymentService.createPayment(paymentParam);

        // 5. 若为 Mock 模式，自动模拟完成支付并流转为“待接单”同时发微信通知
        if (Boolean.TRUE.equals(paymentResult.getIsMockPaid())) {
            mockPaySuccess(orderSn);
        }

        return paymentResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mockPaySuccess(String orderSn) {
        recordPaySuccess(orderSn, "mock", "mock_tx_" + IdUtil.fastSimpleUUID(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResultVO payOrder(Long clientUserId, String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("订单不存在: " + orderSn);
        }
        if (clientUserId == null || !clientUserId.equals(order.getClientUserId())) {
            throw new ApiException("操作被拒绝：只能支付自己的订单");
        }
        if (order.getOrderStatus() != null && order.getOrderStatus() != OrderStatusEnum.PENDING_PAY.getCode()) {
            throw new ApiException("该订单无需支付（当前状态：" + order.getOrderStatus() + "）");
        }

        PaymentService paymentService = paymentStrategyContext.getService();
        SysUser client = sysUserMapper.selectById(clientUserId);
        String clientOpenid = (client != null && client.getOpenid() != null && !client.getOpenid().isEmpty())
                ? client.getOpenid() : "mock_openid";

        CreatePaymentParam param = CreatePaymentParam.builder()
                .orderSn(orderSn)
                .amount(order.getTotalAmount())
                .description("游戏开黑服务 - " + order.getGameName())
                .clientOpenid(clientOpenid)
                .build();

        PaymentResultVO result = paymentService.createPayment(param);
        if (Boolean.TRUE.equals(result.getIsMockPaid())) {
            mockPaySuccess(orderSn);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPaySuccess(String orderSn, String payType, String transactionId, Integer payAmount) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("订单不存在: " + orderSn);
        }

        // 幂等保护：仅允许从「待付款」流转一次，重复回调直接返回，避免重复落支付事实
        if (order.getOrderStatus() != null && order.getOrderStatus() != OrderStatusEnum.PENDING_PAY.getCode()) {
            log.warn("【支付回调幂等拦截】订单号={} 当前状态={}，忽略重复的支付成功回调", orderSn, order.getOrderStatus());
            return;
        }

        int actualAmount = (payAmount != null && payAmount > 0) ? payAmount : order.getTotalAmount();

        // 纯业务状态流转为待接单
        order.setOrderStatus(OrderStatusEnum.PAID_WAIT_ACCEPT.getCode());
        updateById(order);

        // 【金融级支付事实落盘】向 pay_flow 记录真实发生的支付事实与通道扣费，彻底与订单表解耦
        int channelFee = (int) Math.round(actualAmount * currentChannelFeeRate());
        PayFlow payFlow = new PayFlow();
        payFlow.setPaySn("PAY_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6));
        payFlow.setOrderSn(orderSn);
        payFlow.setClientUserId(order.getClientUserId());
        payFlow.setPayType(payType);
        payFlow.setTransactionId(transactionId);
        payFlow.setPayAmount(actualAmount);
        payFlow.setChannelFee(channelFee);
        payFlow.setPayStatus(1); // 1-支付成功
        payFlow.setPayTime(LocalDateTime.now());
        payFlow.setCreateTime(LocalDateTime.now());
        payFlow.setUpdateTime(LocalDateTime.now());
        payFlowMapper.insert(payFlow);

        // 触发平台线下通知 (订阅消息已下线, 改由管理员人工线下通知陪练师)
        if (order.getCompanionUserId() != null) {
            log.info("【下单成功生成通知事件】陪玩师[{}]收到新订单, 等待平台线下通知: orderSn={}",
                    order.getCompanionUserId(), orderSn);
            noticeService.createNewOrderNotice(order.getCompanionUserId(), orderSn, order.getGameName(),
                    order.getTotalAmount(), order.getClientRemark());
        } else {
            log.info("【公共大厅单】该订单无指定陪玩师，流转至派单大厅");
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

        // 指定单：仅指定的陪玩师本人可接单
        // 派单大厅单 (orderType=2)：任意在线陪玩师可抢单，成功后绑定抢单人
        boolean isHallOrder = order.getOrderType() != null && order.getOrderType() == 2;
        if (isHallOrder) {
            if (order.getCompanionUserId() != null && !companionUserId.equals(order.getCompanionUserId())) {
                throw new ApiException("手慢了，该单已被其他陪玩师接走");
            }
            order.setCompanionUserId(companionUserId);
        } else {
            if (!companionUserId.equals(order.getCompanionUserId())) {
                throw new ApiException("非指定接单陪玩师，无法接单");
            }
        }

        // 【准入校验】必须已通过入驻审核才允许接单，防止「仅提交申请」就能抢单接单
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, companionUserId)
        );
        if (profile == null) {
            throw new ApiException("您还不是认证陪练师，请先提交入驻申请并等待审核通过");
        }
        if (profile.getAuditStatus() == null || profile.getAuditStatus() != 1) {
            throw new ApiException(profile.getAuditStatus() != null && profile.getAuditStatus() == 2
                    ? "入驻申请未通过审核，暂时无法接单"
                    : "入驻申请审核中，暂时无法接单");
        }

        order.setOrderStatus(OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode());
        order.setAcceptTime(LocalDateTime.now());
        updateById(order);

        // 陪玩师收到订单后，同步生成一条平台通知事件，便于管理员掌握履约动态
        noticeService.createOrderAcceptedNotice(companionUserId, orderSn, order.getGameName(), order.getTotalAmount());

        log.info("【陪玩师已接单】订单号={}, 陪玩师ID={}, 激活双方IM沟通房间", orderSn, companionUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyFinish(Long companionUserId, String orderSn, String screenshotUrl) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null || !companionUserId.equals(order.getCompanionUserId())) {
            throw new ApiException("无权操作此订单");
        }
        // 状态机校验：仅「服务中」状态可申请结单
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode()) {
            throw new ApiException("当前订单状态不允许申请结单（仅服务中的订单可申请完结）");
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
        if (order == null) {
            throw new ApiException("订单不存在");
        }
        if (clientUserId == null || !clientUserId.equals(order.getClientUserId())) {
            throw new ApiException("无权操作此订单");
        }
        // 状态机校验：仅「陪玩师已申请完结」状态可由客户确认打款
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.APPLIED_FINISH.getCode()) {
            throw new ApiException("当前订单状态不允许确认打款（等待陪玩师提交完结凭证后方可确认）");
        }
        settleAndPayOut(order, "客户确认打款");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoConfirmFinish(String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            return;
        }
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.APPLIED_FINISH.getCode()) {
            return;
        }
        settleAndPayOut(order, "客户超时未确认，系统自动结单");
    }

    /**
     * 【核心清算流程】订单履约完结 → 生成佣金清算单 → 收益划入陪玩师钱包 → 双向记账流水
     * 严禁在订单真正完结前调用本方法（绝不早产分润数据）。
     */
    private void settleAndPayOut(CompanionOrder order, String operatorDesc) {
        Long companionId = order.getCompanionUserId();
        if (companionId == null) {
            throw new ApiException("订单尚未接单，异常订单请联系平台客服处理");
        }

        int totalAmt = order.getTotalAmount();
        double rate = currentCommissionRate();
        int grossFee = (int) Math.round(totalAmt * rate);
        int income = totalAmt - grossFee;
        int channelFee = (int) Math.round(totalAmt * currentChannelFeeRate());
        int netProfit = grossFee - channelFee;

        // 幂等保护：同一订单只允许生成一张清算单
        Long existed = orderSettlementMapper.selectCount(
                new LambdaQueryWrapper<OrderSettlement>().eq(OrderSettlement::getOrderSn, order.getOrderSn()));
        if (existed != null && existed > 0) {
            log.warn("【清算幂等拦截】订单号={} 已存在清算单，跳过重复入账", order.getOrderSn());
            return;
        }

        order.setOrderStatus(OrderStatusEnum.FINISHED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        updateById(order);

        // 1. 【金融级佣金清算事实落盘】在订单真正履约完结时才计算生成清算单 (order_settlement)，绝不早产！
        OrderSettlement settlement = new OrderSettlement();
        settlement.setSettleSn("SETTLE_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6));
        settlement.setOrderSn(order.getOrderSn());
        settlement.setClientUserId(order.getClientUserId());
        settlement.setCompanionUserId(companionId);
        settlement.setTotalAmount(totalAmt);
        settlement.setCompanionIncome(income);
        settlement.setPlatformGrossFee(grossFee);
        settlement.setChannelFee(channelFee);
        settlement.setPlatformNetProfit(netProfit);
        settlement.setFeePayer(1); // 1-平台利润承担通道费
        settlement.setSettleStatus(1); // 1-已清算
        settlement.setSettleTime(LocalDateTime.now());
        settlement.setCreateTime(LocalDateTime.now());
        orderSettlementMapper.insert(settlement);

        // 2. 真实将清算收益划入陪玩师钱包 (user_wallet)
        UserWallet wallet = userWalletMapper.selectOne(
                new LambdaQueryWrapper<UserWallet>().eq(UserWallet::getUserId, companionId)
        );
        if (wallet == null) {
            wallet = new UserWallet();
            wallet.setUserId(companionId);
            wallet.setBalance(0);
            wallet.setFrozenBalance(0);
            wallet.setTotalIncome(0);
            wallet.setVersion(0);
            wallet.setCreateTime(LocalDateTime.now());
            userWalletMapper.insert(wallet);
        }

        int beforeBalance = wallet.getBalance();
        wallet.setBalance(beforeBalance + income);
        wallet.setTotalIncome(wallet.getTotalIncome() + income);
        wallet.setUpdateTime(LocalDateTime.now());
        // 乐观锁：版本号不匹配时 updateById 影响行数为 0，此处必须中断事务，防止余额被覆盖丢失
        if (userWalletMapper.updateById(wallet) < 1) {
            throw new ApiException("钱包并发更新冲突（乐观锁校验失败），请重试！");
        }

        // 3. 真实记录钱包明细流水 (wallet_flow)
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

        // 4. 陪玩师个人主档的已接单总量累加 +1
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>().eq(CompanionProfile::getUserId, companionId)
        );
        if (profile != null) {
            profile.setOrderCount(profile.getOrderCount() + 1);
            companionProfileMapper.updateById(profile);
        }

        log.info("【订单完结并清算入账】{} 划扣收益 {}分 入陪玩师[{}]钱包, 平台净利: {}分 (毛抽佣{}分 - 微信通道费{}分)",
                operatorDesc, income, companionId, netProfit, grossFee, channelFee);
    }

    /**
     * 【热配置】获取当前生效的微信通道费率。
     *
     * <p>优先读取管理员热配置中心的 {@code platform.channel-fee-rate}（支持百分数，如 0.6 表示 0.6%），
     * 缺失时回退到启动配置。</p>
     */
    private double currentChannelFeeRate() {
        try {
            String rateStr = platformConfigService.getConfig("platform.channel-fee-rate", null);
            if (rateStr != null && !rateStr.trim().isEmpty()) {
                double rate = Double.parseDouble(rateStr.trim());
                if (rate >= 0 && rate <= 10) {
                    return rate / 100.0;
                }
            }
        } catch (Exception e) {
            log.warn("读取通道费率热配置失败, 回退默认值: {}", e.getMessage());
        }
        return (channelFeeRate == null) ? 0.006 : channelFeeRate;
    }

    /**
     * 【热配置】获取当前生效的抽佣比例。
     * 管理员在手机端修改「平台抽佣比例」后，秒级生效（无需重启服务）。
     */
    private double currentCommissionRate() {
        try {
            String percentStr = platformConfigService.getConfig("platform.commission-percent", null);
            if (percentStr != null && !percentStr.trim().isEmpty()) {
                double percent = Double.parseDouble(percentStr.trim());
                if (percent >= 0 && percent <= 100) {
                    return percent / 100.0;
                }
            }
        } catch (Exception e) {
            log.warn("读取抽佣热配置失败, 回退默认值: {}", e.getMessage());
        }
        return (commissionRate == null) ? 0.10 : commissionRate;
    }

    @Override
    public CompanionOrder getOrderDetail(String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("未找到该订单: " + orderSn);
        }
        // 领域聚合：动态装配关联的支付事实流水与清算单
        PayFlow payFlow = payFlowMapper.selectOne(
                new LambdaQueryWrapper<PayFlow>().eq(PayFlow::getOrderSn, orderSn)
        );
        order.setPayFlow(payFlow);

        OrderSettlement settlement = orderSettlementMapper.selectOne(
                new LambdaQueryWrapper<OrderSettlement>().eq(OrderSettlement::getOrderSn, orderSn)
        );
        order.setSettlement(settlement);

        // 动态装配预计/实际收益 (解决待接单时显示0.00的问题)
        fillEstimatedIncome(order);
        // 动态装配当前阶段倒计时 (服务端按绝对截止时间计算, 前端每次进入都不会重置)
        fillRemainingSeconds(order);

        return order;
    }

    /**
     * 【倒计时】按订单的**绝对截止时间**计算当前阶段剩余秒数。
     *
     * <p>状态 1（待接单）：下单时间 + 接单超时阈值 → 超过未接单自动全额退款</p>
     * <p>状态 2（服务中）：接单时间 + 履约超时阈值 → 超过未完成服务自动全额退款</p>
     * <p>状态 3（待客户确认）：接单时间 + 履约超时阈值 → 超过由系统自动结单</p>
     */
    private void fillRemainingSeconds(CompanionOrder order) {
        if (order == null || order.getOrderStatus() == null) {
            return;
        }
        LocalDateTime deadline = null;
        if (order.getOrderStatus() == OrderStatusEnum.PAID_WAIT_ACCEPT.getCode()) {
            if (order.getCreateTime() != null) {
                deadline = order.getCreateTime().plusMinutes(acceptTimeoutMinutes);
            }
        } else if (order.getOrderStatus() == OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode()
                || order.getOrderStatus() == OrderStatusEnum.APPLIED_FINISH.getCode()) {
            if (order.getAcceptTime() != null) {
                deadline = order.getAcceptTime().plusMinutes(serviceTimeoutMinutes);
            }
        }
        if (deadline == null) {
            return;
        }
        order.setDeadlineTime(deadline);
        long seconds = java.time.Duration.between(LocalDateTime.now(), deadline).getSeconds();
        order.setRemainingSeconds((int) Math.max(0, Math.min(seconds, Integer.MAX_VALUE)));
    }

    @Override
    public CompanionOrder getOrderDetailForViewer(String orderSn, Long viewerUserId, int viewerRole) {
        CompanionOrder order = getOrderDetail(orderSn);

        // 【数据可见性】平台内部清算数据（毛抽佣/通道费/净利）仅陪玩师本人与管理员可见，
        // 普通客户的订单详情中直接剥离，避免通过抓包即可窥探平台利润结构。
        boolean canSeeSettlement = viewerRole == RoleEnum.ADMIN.getCode()
                || (viewerUserId != null && viewerUserId.equals(order.getCompanionUserId()));
        if (!canSeeSettlement) {
            order.setSettlement(null);
            order.setPlatformFee(null);
        }
        return order;
    }

    @Override
    public boolean canViewOrder(Long viewerUserId, int viewerRole, CompanionOrder order) {
        if (order == null) {
            return false;
        }
        if (viewerRole == RoleEnum.ADMIN.getCode()) {
            return true;
        }
        if (viewerUserId == null) {
            return false;
        }
        return viewerUserId.equals(order.getClientUserId())
                || viewerUserId.equals(order.getCompanionUserId())
                || viewerUserId.equals(order.getOriginalCompanionId());
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

        // 状态筛选语义：
        //  -1 = 全部（不过滤）
        //  -2 = 已结束（已取消5 / 已退款6 / 维权争议中7 / 仲裁退款完成8）
        //  0~8 = 精确匹配单个状态
        if (orderStatus != null && orderStatus >= 0) {
            wrapper.eq(CompanionOrder::getOrderStatus, orderStatus);
        } else if (orderStatus != null && orderStatus == ORDER_STATUS_FINISHED_GROUP) {
            wrapper.in(CompanionOrder::getOrderStatus, 5, 6, 7, 8);
        }

        wrapper.orderByDesc(CompanionOrder::getCreateTime);
        Page<CompanionOrder> resPage = page(page, wrapper);
        if (resPage.getRecords() != null) {
            resPage.getRecords().forEach(o -> {
                fillEstimatedIncome(o);
                fillRemainingSeconds(o);
            });
        }
        return resPage;
    }

    private void fillEstimatedIncome(CompanionOrder order) {
        if (order == null) return;
        if (order.getSettlement() != null && order.getSettlement().getCompanionIncome() != null) {
            order.setCompanionIncome(order.getSettlement().getCompanionIncome());
            order.setPlatformFee(order.getSettlement().getPlatformGrossFee());
        } else {
            int totalAmt = order.getTotalAmount() != null ? order.getTotalAmount() : 0;
            int fee = (int) Math.round(totalAmt * currentCommissionRate());
            order.setPlatformFee(fee);
            order.setCompanionIncome(totalAmt - fee);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAndRefundOrder(Long clientUserId, String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("未找到该订单");
        }
        if (clientUserId == null || !clientUserId.equals(order.getClientUserId())) {
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
        updateById(order);

        // 同步冲正资金支付流水表 (pay_flow，退款事实由流水表承载)
        PayFlow payFlow = payFlowMapper.selectOne(
                new LambdaQueryWrapper<PayFlow>().eq(PayFlow::getOrderSn, orderSn)
        );
        if (payFlow != null) {
            payFlow.setPayStatus(3); // 3-已全额退款
            payFlow.setRefundAmount(order.getTotalAmount());
            payFlow.setRefundChannelFee(payFlow.getChannelFee()); // 冲正微信通道费
            payFlow.setUpdateTime(LocalDateTime.now());
            payFlowMapper.updateById(payFlow);
        }

        log.info("【客户主动撤单并退款成功】订单号={}, 客户ID={}, 全额退款={}分, 支付流水已冲正",
                orderSn, clientUserId, order.getTotalAmount());
    }

    /**
     * 【扩展架构设计提示】
     * 按照规范的财务核算与支付审计原则，加钟续单应当通过独立的加钟子订单 (addon_sub_order)
     * 或独立的支付单 (payment_record) 进行派生，避免直接污染原订单初始购买数量及可能发生的重名商户单号异常。
     * 当前版本暂时下线该直接累加逻辑，保留接口定义作为后续加钟子订单模块的扩展切入点。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResultVO addOnOrder(Long clientUserId, String orderSn, int addQuantity) {
        log.warn("加钟续单功能已进入解耦扩展模式，当前接口保留作为扩展切入点。订单号: {}", orderSn);
        throw new ApiException("加钟续单功能正在升级为独立子订单体系，当前暂未开放，敬请期待！");
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

        // 真实超时判定：以「下单时间 + 超时阈值」与当前时间比较
        LocalDateTime deadline = order.getCreateTime() == null
                ? null
                : order.getCreateTime().plusMinutes(acceptTimeoutMinutes);
        if (deadline == null || LocalDateTime.now().isBefore(deadline)) {
            return false;
        }

        // 触发自动原路退款
        cancelAndRefundOrder(order.getClientUserId(), orderSn);
        log.info("【接单超时自动退款触发】订单[{}]陪练师超时未接单，已自动为客户[{}]原路退款并冲正流水！", orderSn, order.getClientUserId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndRefundIfServiceTimeout(String orderSn) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) return false;

        // 只有服务中(2)才需要判定履约超时
        if (order.getOrderStatus() == null || order.getOrderStatus() != OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode()) {
            return false;
        }

        LocalDateTime deadline = order.getAcceptTime() == null
                ? null
                : order.getAcceptTime().plusMinutes(serviceTimeoutMinutes);
        if (deadline == null || LocalDateTime.now().isBefore(deadline)) {
            return false;
        }

        // 触发自动原路退款（服务超时，与客户主动撤单走同一套资金冲正逻辑）
        PaymentService paymentService = paymentStrategyContext.getService();
        boolean refundSuccess = paymentService.refund(orderSn, order.getTotalAmount(), order.getTotalAmount(),
                "陪玩师接单后 " + serviceTimeoutMinutes + " 分钟未完成服务，系统自动全额退款");
        if (!refundSuccess) {
            throw new ApiException("服务超时自动退款调用支付网关失败");
        }

        order.setOrderStatus(OrderStatusEnum.REFUNDED.getCode()); // 6 已退款
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);

        // 冲正支付事实流水
        PayFlow payFlow = payFlowMapper.selectOne(
                new LambdaQueryWrapper<PayFlow>().eq(PayFlow::getOrderSn, orderSn)
        );
        if (payFlow != null) {
            payFlow.setPayStatus(3);
            payFlow.setRefundAmount(order.getTotalAmount());
            payFlow.setRefundChannelFee(payFlow.getChannelFee());
            payFlow.setUpdateTime(LocalDateTime.now());
            payFlowMapper.updateById(payFlow);
        }

        // 聊天室内同步系统提示，避免双方信息不对称
        ChatMessage sysMsg = new ChatMessage();
        sysMsg.setOrderSn(orderSn);
        sysMsg.setSenderId(0L);
        sysMsg.setSenderName("系统提示");
        sysMsg.setSenderAvatar("https://img.icons8.com/color/96/shield.png");
        sysMsg.setContent("⏰ 陪玩师接单后超过 " + serviceTimeoutMinutes + " 分钟仍未完成服务，系统已为客户全额原路退款，本单已结束。");
        sysMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(sysMsg);

        log.info("【服务超时自动退款触发】订单[{}]陪玩师接单后超时未完成服务，已自动为客户[{}]全额退款！",
                orderSn, order.getClientUserId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyDispute(Long clientUserId, String orderSn, String reason) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("未找到该订单: " + orderSn);
        }
        if (clientUserId == null || !clientUserId.equals(order.getClientUserId())) {
            throw new ApiException("操作被拒绝：只有下单客户本人才能发起售后维权");
        }
        // 仅在服务中(2)或待确认(3)时允许发起维权争议
        if (order.getOrderStatus() != OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode()
                && order.getOrderStatus() != OrderStatusEnum.APPLIED_FINISH.getCode()) {
            throw new ApiException("当前订单状态不允许发起维权仲裁");
        }

        order.setOrderStatus(OrderStatusEnum.DISPUTING.getCode());
        order.setDisputeReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "客户申请平台介入处理服务争议");
        order.setDisputeTime(LocalDateTime.now());
        updateById(order);

        // 向开黑聊天室发送系统维权广播
        ChatMessage sysMsg = new ChatMessage();
        sysMsg.setOrderSn(orderSn);
        sysMsg.setSenderId(0L);
        sysMsg.setSenderName("平台维权中心");
        sysMsg.setSenderAvatar("https://img.icons8.com/color/96/shield.png");
        sysMsg.setContent("⚠️ 客户已发起售后维权争议申请，平台管理员已介入核实，当前订单暂停自动结算！");
        sysMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(sysMsg);

        // 生成平台待通知事件, 提示管理员尽快线下介入
        noticeService.createDisputeNotice(order.getClientUserId(), orderSn, order.getDisputeReason());

        log.info("【客户发起维权争议】订单号={}, 客户ID={}, 争议原因={}", orderSn, clientUserId, order.getDisputeReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditDispute(Long adminUserId, String orderSn, boolean refundToClient, String remark) {
        CompanionOrder order = getOne(new LambdaQueryWrapper<CompanionOrder>().eq(CompanionOrder::getOrderSn, orderSn));
        if (order == null) {
            throw new ApiException("未找到该订单: " + orderSn);
        }
        if (order.getOrderStatus() != OrderStatusEnum.DISPUTING.getCode()) {
            throw new ApiException("该订单未处于维权争议中，无需仲裁");
        }

        if (refundToClient) {
            // 仲裁支持客户：全额退款
            PaymentService paymentService = paymentStrategyContext.getService();
            boolean refundSuccess = paymentService.refund(orderSn, order.getTotalAmount(), order.getTotalAmount(), "维权仲裁退款: " + remark);
            if (!refundSuccess) {
                throw new ApiException("仲裁退款调用支付网关失败");
            }
            order.setOrderStatus(OrderStatusEnum.DISPUTE_REFUNDED.getCode());
            order.setDisputeResult("仲裁通过，全额退款给客户。处理意见: " + remark);

            // 同步冲正支付流水 (pay_flow)
            PayFlow payFlow = payFlowMapper.selectOne(
                    new LambdaQueryWrapper<PayFlow>().eq(PayFlow::getOrderSn, orderSn)
            );
            if (payFlow != null) {
                payFlow.setPayStatus(3);
                payFlow.setRefundAmount(order.getTotalAmount());
                payFlow.setRefundChannelFee(payFlow.getChannelFee());
                payFlow.setUpdateTime(LocalDateTime.now());
                payFlowMapper.updateById(payFlow);
            }
        } else {
            // 仲裁驳回客户维权：恢复为原待确认或服务中状态
            if (order.getFinishScreenshot() != null && !order.getFinishScreenshot().isEmpty()) {
                order.setOrderStatus(OrderStatusEnum.APPLIED_FINISH.getCode());
            } else {
                order.setOrderStatus(OrderStatusEnum.ACCEPTED_IN_SERVICE.getCode());
            }
            order.setDisputeResult("仲裁驳回客户申请，继续履约。处理意见: " + remark);
        }

        updateById(order);
        log.info("【管理员完成维权仲裁】订单号={}, 仲裁人ID={}, 是否退款={}, 结果={}",
                orderSn, adminUserId, refundToClient, order.getDisputeResult());
    }
}
