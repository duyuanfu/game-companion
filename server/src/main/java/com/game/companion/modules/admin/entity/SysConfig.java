package com.game.companion.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_config")
@Schema(description = "平台全局动态配置表")
public class SysConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "配置键名")
    private String configKey;

    @Schema(description = "配置内容")
    private String configValue;

    @Schema(description = "说明备注")
    private String remark;

    private LocalDateTime updateTime;
}
