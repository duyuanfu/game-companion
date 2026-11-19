package com.game.companion.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * 全局跨域配置。
 *
 * <p>默认值为 * 仅用于本地联调；生产环境通过 CORS_ALLOWED_ORIGINS 设置允许的 Web 域名，
 * 多个域名用逗号分隔。最推荐的做法仍然是 Nginx 同源代理，从源头消除跨域。</p>
 *
 * <p>安全约束：使用通配符 Origin 时禁止携带浏览器凭据，避免任意站点读取登录态响应。
 * 本项目 Web 端通过 satoken 请求头鉴权，不依赖 Cookie，因此关闭 credentials 不影响正常使用。</p>
 */
@Slf4j
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        String raw = allowedOrigins == null ? "*" : allowedOrigins.trim();

        if ("*".equals(raw) || raw.isEmpty()) {
            config.addAllowedOriginPattern("*");
            // 关键安全修复：Allow-Origin: * 与 credentials 不能同时开启
            config.setAllowCredentials(false);
            log.warn("【CORS】当前允许所有 Origin 且已关闭 credentials；生产环境请配置 CORS_ALLOWED_ORIGINS");
        } else {
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .forEach(config::addAllowedOrigin);
            config.setAllowCredentials(true);
        }

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
