package com.game.companion.modules.order.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.common.enums.OrderStatusEnum;
import com.game.companion.modules.order.entity.CompanionOrder;
import com.game.companion.modules.order.mapper.CompanionOrderMapper;
import com.game.companion.modules.order.service.CompanionOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【订单超时调度器】
 *
 * <p>补齐历史缺失的自动化能力：</p>
 * <ul>
 *   <li><b>接单超时自动退款</b>：订单支付后超过 {@code platform.order-accept-timeout-minutes}
 *       分钟仍未被陪玩师接单，系统自动触发原路全额退款并冲正支付事实流水；</li>
 *   <li><b>完结超时自动结单</b>：陪玩师已提交完结凭证，但客户超过
 *       {@code platform.order-finish-timeout-hours} 小时未确认打款，系统自动完结并清算入账。</li>
 * </ul>
 *
 * <p>此前该能力仅暴露为需要外部手动调用的 HTTP 接口，线上不会真正自动执行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final CompanionOrderMapper companionOrderMapper;
    private final CompanionOrderService companionOrderService;

    /**
     * 单次扫描的最大处理条数，避免极端积压时单次事务过大拖垮数据库。
     */
    private static final int BATCH_LIMIT = 200;

    @Value("${platform.order-accept-timeout-minutes:10}")
    private int acceptTimeoutMinutes;

    @Value("${platform.order-finish-timeout-hours:24}")
    private int finishTimeoutHours;

    /**
     * 每 60 秒扫描一次超时订单。
     */
    @Scheduled(fixedDelayString = "${platform.scheduler.scan-interval-ms:60000}", initialDelay = 30000)
    public void scanTimeoutOrders() {
        try {
            int refunded = refundAcceptTimeoutOrders();
            int settled = settleFinishTimeoutOrders();
            if (refunded > 0 || settled > 0) {
                log.info("【超时调度扫描完成】自动退款 {} 单，自动结单 {} 单", refunded, settled);
            }
        } catch (Exception e) {
            log.error("【超时调度扫描异常】", e);
        }
    }

    /**
     * 处理接单超时未接单的订单：自动原路全额退款。
     *
     * <p>刻意不加类级事务：每一笔退款由 service 层各自开启独立事务，
     * 避免单笔失败导致整批回滚。</p>
     */
    public int refundAcceptTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(acceptTimeoutMinutes);
        List<CompanionOrder> timeoutOrders = companionOrderMapper.selectList(
                new LambdaQueryWrapper<CompanionOrder>()
                        .eq(CompanionOrder::getOrderStatus, OrderStatusEnum.PAID_WAIT_ACCEPT.getCode())
                        .lt(CompanionOrder::getCreateTime, deadline)
                        .orderByAsc(CompanionOrder::getCreateTime)
                        .last("LIMIT " + BATCH_LIMIT)
        );

        int count = 0;
        for (CompanionOrder order : timeoutOrders) {
            try {
                if (companionOrderService.checkAndRefundIfTimeout(order.getOrderSn())) {
                    count++;
                }
            } catch (Exception e) {
                log.error("【接单超时自动退款失败】订单号={}: {}", order.getOrderSn(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * 处理客户超时未确认的已结单申请订单：自动完结并清算入账。
     */
    public int settleFinishTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(finishTimeoutHours);
        List<CompanionOrder> pendingOrders = companionOrderMapper.selectList(
                new LambdaQueryWrapper<CompanionOrder>()
                        .eq(CompanionOrder::getOrderStatus, OrderStatusEnum.APPLIED_FINISH.getCode())
                        .lt(CompanionOrder::getUpdateTime, deadline)
                        .orderByAsc(CompanionOrder::getUpdateTime)
                        .last("LIMIT " + BATCH_LIMIT)
        );

        int count = 0;
        for (CompanionOrder order : pendingOrders) {
            try {
                companionOrderService.autoConfirmFinish(order.getOrderSn());
                count++;
            } catch (Exception e) {
                log.error("【完结超时自动结单失败】订单号={}: {}", order.getOrderSn(), e.getMessage());
            }
        }
        return count;
    }
}
