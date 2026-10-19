package com.game.companion.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatusEnum {
    PENDING_PAY(0, "待付款"),
    PAID_WAIT_ACCEPT(1, "已付款待接单(派单中)"),
    ACCEPTED_IN_SERVICE(2, "已接单/服务中(沟通中)"),
    APPLIED_FINISH(3, "陪玩师申请完结"),
    FINISHED(4, "订单已完成"),
    CANCELLED(5, "已关闭/取消"),
    REFUNDED(6, "已退款");

    private final int code;
    private final String desc;

    public static OrderStatusEnum getByCode(int code) {
        for (OrderStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
