package com.game.companion.modules.common.controller;

import com.game.companion.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【服务自检网关】
 *
 * <p>小程序端在「静默重登仍失败」时会调用本接口确认后端身份：</p>
 * <ul>
 *   <li>返回的 {@code app} 必须等于 {@code game-companion}，否则说明 {@code BASE_URL}
 *       指向了别的服务（例如本机 8080 上运行的其它项目），前端会给出明确提示；</li>
 *   <li>同时回显当前服务端口，便于一眼核对小程序侧的地址配置。</li>
 * </ul>
 */
@Tag(name = "服务自检网关", description = "供小程序端确认后端服务身份与端口，避免连错服务")
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    /**
     * 服务唯一标识，小程序端据此判断 BASE_URL 是否指向了正确的后端。
     */
    public static final String APP_ID = "game-companion";

    @Value("${server.port:8088}")
    private int serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @GetMapping
    @Operation(summary = "服务健康检查与身份标识")
    public CommonResult<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", APP_ID);
        data.put("name", "开黑搭子 (Game Companion)");
        data.put("port", serverPort);
        data.put("contextPath", contextPath);
        data.put("baseUrl", "http://<host>:" + serverPort + contextPath);
        return CommonResult.success(data);
    }
}
