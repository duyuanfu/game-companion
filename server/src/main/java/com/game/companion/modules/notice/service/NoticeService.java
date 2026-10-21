package com.game.companion.modules.notice.service;

import com.game.companion.modules.notice.vo.NoticeItemVO;

import java.util.List;

public interface NoticeService {

    /**
     * 【新订单待接单】客户下单并支付成功后，生成一条待通知事件，交由平台管理员线下通知陪练师。
     *
     * @param companionUserId 陪玩师用户ID
     * @param orderSn         订单编号
     * @param gameName        游戏名称
     * @param totalAmount     订单总额 (分)
     * @param clientRemark    客户备注要求
     */
    void createNewOrderNotice(Long companionUserId, String orderSn, String gameName,
                              Integer totalAmount, String clientRemark);

    /**
     * 【陪练师已接单】陪玩师接单后生成事件，便于平台掌握履约动态。
     */
    void createOrderAcceptedNotice(Long companionUserId, String orderSn, String gameName, Integer totalAmount);

    /**
     * 【入驻审核结果】生成待通知事件，由平台管理员线下告知申请人审核结果。
     */
    void createAuditResultNotice(Long userId, boolean isApproved, String reason);

    /**
     * 【售后维权】客户发起维权后生成事件，提示平台介入线下协调。
     */
    void createDisputeNotice(Long clientUserId, String orderSn, String reason);

    /**
     * 【管理员工作台】查询待线下通知的事件列表
     */
    List<NoticeItemVO> getPendingNotices(int limit);

    /**
     * 【管理员工作台】标记某条事件已完成线下通知
     */
    void markNotified(Long noticeId, String remark);
}
