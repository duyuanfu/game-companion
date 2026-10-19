package com.game.companion.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode implements IResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(400, "参数检验失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    
    // 业务状态码
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_DISABLED(1002, "账号已被禁用"),
    COMPANION_NOT_FOUND(2001, "陪玩师不存在或未通过审核"),
    COMPANION_OFFLINE(2002, "陪玩师当前休息中，暂不可接单"),
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_ERROR(3002, "订单状态不匹配，操作失败"),
    ORDER_GRAB_FAILED(3003, "手慢了，该单已被抢走"),
    WALLET_BALANCE_NOT_ENOUGH(4001, "钱包可提现余额不足");

    private final long code;
    private final String message;
}
