package com.game.companion.modules.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_wallet")
@Schema(description = "用户资金钱包实体")
public class UserWallet implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer balance;

    private Integer frozenBalance;

    private Integer totalIncome;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
