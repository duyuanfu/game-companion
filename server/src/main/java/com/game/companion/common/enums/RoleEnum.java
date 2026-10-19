package com.game.companion.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoleEnum {
    CLIENT(1, "普通客户"),
    COMPANION(2, "陪玩师"),
    ADMIN(9, "平台超级管理员");

    private final int code;
    private final String desc;
}
