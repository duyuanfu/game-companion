package com.game.companion.modules.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.game.companion.common.api.CommonResult;
import com.game.companion.modules.user.dto.BindPhoneDTO;
import com.game.companion.modules.user.dto.WxLoginDTO;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.service.UserService;
import com.game.companion.modules.user.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户与微信授权中心", description = "包含微信一键静默登录、手机号一键授权绑定")
public class UserController {

    private final UserService userService;

    @Autowired(required = false)
    private javax.sql.DataSource dataSource;

    @PostMapping("/wx-login")
    @Operation(summary = "微信一键静默登录 (零感知建档)")
    public CommonResult<UserLoginVO> wxLogin(@Valid @RequestBody WxLoginDTO dto) {
        UserLoginVO loginVO = userService.wxSilentLogin(dto);
        return CommonResult.success(loginVO);
    }

    @GetMapping("/info")
    @Operation(summary = "从数据库查询当前用户完整档案信息")
    public CommonResult<SysUser> getUserInfo(@RequestParam(required = false) Long userId) {
        SysUser user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        if (user == null && cn.dev33.satoken.stp.StpUtil.isLogin()) {
            user = userService.getById(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
        }
        return CommonResult.success(user);
    }

    @PostMapping("/bind-phone")
    @Operation(summary = "微信原生 getPhoneNumber 一键授权绑定手机号 (防漏单)")
    public CommonResult<String> bindPhone(@Valid @RequestBody BindPhoneDTO dto, 
                                          @RequestParam(required = false) Long userId) {
        Long targetUserId = userId;
        if (targetUserId == null || targetUserId <= 0) {
            targetUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : 10000L;
        }
        String phone = userService.bindWxPhoneNumber(targetUserId, dto);
        return CommonResult.success(phone, "手机号绑定并写入数据库成功");
    }

    @PostMapping("/mock-switch-role")
    @Operation(summary = "【开发环境特权】快速切换当前用户身份 (1-客户 2-陪玩 9-管理员)")
    public CommonResult<String> mockSwitchRole(@RequestParam Long userId, @RequestParam Integer role) {
        SysUser user = userService.getById(userId);
        if (user != null) {
            user.setRole(role);
            userService.updateById(user);
            StpUtil.login(userId);
            return CommonResult.success("身份切换成功，当前角色代码: " + role);
        }
        return CommonResult.failed("用户不存在");
    }

    @GetMapping("/debug/all")
    @Operation(summary = "【诊断神器】打印后端真正连接的数据库与sys_user全量数据")
    public CommonResult<java.util.Map<String, Object>> debugAllUsers() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        
        // 1. 获取 Spring Boot 当前真正连接的数据库信息
        try {
            if (dataSource != null) {
                try (java.sql.Connection conn = dataSource.getConnection()) {
                    map.put("当前连接的数据库URL", conn.getMetaData().getURL());
                    map.put("当前连接的数据库用户名", conn.getMetaData().getUserName());
                    map.put("数据库产品名称与版本", conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
                }
            }
        } catch (Exception e) {
            map.put("获取数据源信息失败", e.getMessage());
        }

        // 2. 从数据库直接查出 sys_user 表的所有记录
        java.util.List<SysUser> allUsers = userService.list();
        map.put("sys_user当前总记录数", allUsers.size());
        map.put("sys_user全量记录列表", allUsers);

        return CommonResult.success(map);
    }
}
