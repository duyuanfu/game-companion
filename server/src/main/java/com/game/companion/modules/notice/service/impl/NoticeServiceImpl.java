package com.game.companion.modules.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.common.exception.ApiException;
import com.game.companion.modules.notice.entity.NoticeLog;
import com.game.companion.modules.notice.mapper.NoticeLogMapper;
import com.game.companion.modules.notice.service.NoticeService;
import com.game.companion.modules.notice.vo.NoticeItemVO;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 【平台线下通知服务】
 *
 * <p><b>设计变更说明</b>：微信小程序订阅消息为<b>一次性</b>机制——用户每次授权仅获得 1 次接收配额，
 * 陪玩师若未及时授权或配额耗尽，就会出现「老板下单但陪练师完全不知情」的漏单。
 * 因此平台不再依赖订阅消息，改用<b>管理员线下兜底通知</b>：</p>
 *
 * <ol>
 *   <li>所有需要触达用户的事件（新订单、已接单、审核结果、维权）一律落库为「待通知」记录；</li>
 *   <li>管理员在手机端工作台实时看到待通知队列（含陪练师手机号），执行电话 / 微信 / 短信线下通知；</li>
 *   <li>小程序内的红点角标、马达震动、Toast 提示等<b>站内提示</b>继续保留，兼顾即时性。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    /**
     * 通知渠道：5-平台线下通知
     */
    private static final int CHANNEL_OFFLINE = 5;

    private static final int TYPE_NEW_ORDER = 1;
    private static final int TYPE_ORDER_ACCEPTED = 2;
    private static final int TYPE_AUDIT_RESULT = 3;
    private static final int TYPE_DISPUTE = 4;

    private final NoticeLogMapper noticeLogMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public void createNewOrderNotice(Long companionUserId, String orderSn, String gameName,
                                     Integer totalAmount, String clientRemark) {
        String remark = (clientRemark != null && !clientRemark.trim().isEmpty())
                ? clientRemark.trim() : "客户无特别要求";
        String content = String.format("【新订单】%s 客户已支付 %.2f 元下单「%s」，请尽快接单！客户要求：%s",
                resolveUserName(companionUserId),
                (totalAmount == null ? 0 : totalAmount) / 100.0,
                gameName == null ? "开黑服务" : gameName,
                remark);
        saveNotice(TYPE_NEW_ORDER, companionUserId, orderSn, "新订单待接单", content, totalAmount);
    }

    @Override
    public void createOrderAcceptedNotice(Long companionUserId, String orderSn, String gameName, Integer totalAmount) {
        String content = String.format("【已接单】%s 已接下「%s」订单，金额 %.2f 元，请关注履约进度。",
                resolveUserName(companionUserId),
                gameName == null ? "开黑服务" : gameName,
                (totalAmount == null ? 0 : totalAmount) / 100.0);
        saveNotice(TYPE_ORDER_ACCEPTED, companionUserId, orderSn, "陪练师已接单", content, totalAmount);
    }

    @Override
    public void createAuditResultNotice(Long userId, boolean isApproved, String reason) {
        String content = isApproved
                ? String.format("【审核通过】%s 的陪练师入驻申请已通过，可进入接单工作台。",
                resolveUserName(userId))
                : String.format("【审核驳回】%s 的陪练师入驻申请未通过，原因：%s。请线下告知申请人并引导重新提交。",
                resolveUserName(userId), (reason == null || reason.trim().isEmpty()) ? "资质不符" : reason.trim());
        saveNotice(TYPE_AUDIT_RESULT, userId, null, isApproved ? "入驻审核通过" : "入驻审核驳回", content, null);
    }

    @Override
    public void createDisputeNotice(Long clientUserId, String orderSn, String reason) {
        String content = String.format("【售后维权】订单 %s 客户发起维权，原因：%s。请平台尽快介入协调。",
                orderSn == null ? "-" : orderSn,
                (reason == null || reason.trim().isEmpty()) ? "未说明" : reason.trim());
        saveNotice(TYPE_DISPUTE, clientUserId, orderSn, "客户发起维权", content, null);
    }

    @Override
    public List<NoticeItemVO> getPendingNotices(int limit) {
        List<NoticeLog> logs = noticeLogMapper.selectList(
                new LambdaQueryWrapper<NoticeLog>()
                        .eq(NoticeLog::getSendStatus, 0)
                        .orderByDesc(NoticeLog::getCreateTime)
                        .last("LIMIT " + Math.max(1, limit))
        );
        List<NoticeItemVO> result = new ArrayList<>();
        for (NoticeLog item : logs) {
            result.add(NoticeItemVO.builder()
                    .id(item.getId())
                    .orderSn(item.getOrderSn())
                    .noticeType(item.getNoticeType())
                    .typeText(typeText(item.getNoticeType()))
                    .title(item.getTitle())
                    .content(item.getContent())
                    .targetUserId(item.getTargetUserId())
                    .userName(item.getUserName())
                    .phone(item.getPhone())
                    .amount(item.getAmount())
                    .createTime(item.getCreateTime())
                    .build());
        }
        return result;
    }

    @Override
    public void markNotified(Long noticeId, String remark) {
        NoticeLog notice = noticeLogMapper.selectById(noticeId);
        if (notice == null) {
            throw new ApiException("未找到该待通知事件");
        }
        if (notice.getSendStatus() != null && notice.getSendStatus() == 1) {
            throw new ApiException("该事件已通知过了，请勿重复操作");
        }
        notice.setSendStatus(1);
        notice.setResponseMsg(remark == null || remark.trim().isEmpty() ? "已完成线下通知" : remark.trim());
        notice.setFinishTime(LocalDateTime.now());
        noticeLogMapper.updateById(notice);
        log.info("【平台线下通知完成】事件ID={}, 订单={}, 备注={}", noticeId, notice.getOrderSn(), notice.getResponseMsg());
    }

    /**
     * 落库一条待通知事件
     */
    private void saveNotice(int noticeType, Long targetUserId, String orderSn,
                            String title, String content, Integer amount) {
        try {
            SysUser user = targetUserId == null ? null : sysUserMapper.selectById(targetUserId);
            NoticeLog notice = new NoticeLog();
            notice.setOrderSn(orderSn == null ? "" : orderSn);
            notice.setTargetUserId(targetUserId == null ? 0L : targetUserId);
            notice.setNoticeType(noticeType);
            notice.setChannel(CHANNEL_OFFLINE);
            notice.setTitle(title);
            notice.setContent(content);
            notice.setUserName(user != null ? user.getNickname() : "用户");
            notice.setPhone(user != null ? user.getPhone() : null);
            notice.setAmount(amount);
            notice.setSendStatus(0); // 0-待通知
            notice.setCreateTime(LocalDateTime.now());
            noticeLogMapper.insert(notice);
            log.info("【生成待通知事件】类型={}, 订单={}, 被通知人={}({}), 内容={}",
                    title, orderSn, notice.getUserName(), notice.getPhone(), content);
        } catch (Exception e) {
            // 通知事件落库失败不允许影响主业务链路
            log.error("【生成待通知事件失败】不影响主流程, error={}", e.getMessage());
        }
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return "用户";
        }
        SysUser user = sysUserMapper.selectById(userId);
        return (user != null && user.getNickname() != null) ? user.getNickname() : ("用户" + userId);
    }

    private String typeText(Integer noticeType) {
        if (noticeType == null) {
            return "通知";
        }
        switch (noticeType) {
            case TYPE_NEW_ORDER: return "新订单";
            case TYPE_ORDER_ACCEPTED: return "已接单";
            case TYPE_AUDIT_RESULT: return "审核结果";
            case TYPE_DISPUTE: return "售后维权";
            default: return "通知";
        }
    }
}
