package com.game.companion.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditStatusEnum {
    PENDING(0, "待审核"),
    APPROVED(1, "审核通过"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String desc;
}
