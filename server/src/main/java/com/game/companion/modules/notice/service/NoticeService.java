package com.game.companion.modules.notice.service;

public interface NoticeService {

    /**
     * 向陪玩师推送新订单到达提醒
     *
     * @param companionUserId 陪玩师用户ID
     * @param orderSn 订单编号
     * @param gameName 游戏名称
     * @param totalAmount 订单总额 (分)
     * @param clientRemark 客户备注要求
     */
    void sendNewOrderNotice(Long companionUserId, String orderSn, String gameName, Integer totalAmount, String clientRemark);

    /**
     * 向入驻申请人推送审核结果 (通过/驳回)
     */
    void sendAuditResultNotice(Long userId, boolean isApproved, String reason);
}
