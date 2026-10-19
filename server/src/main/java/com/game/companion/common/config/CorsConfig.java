package com.game.companion.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局跨域 (CORS) 允许配置
 * 解决 HBuilderX 在浏览器 (H5) 模式下调试本地接口时的跨域拦截问题
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许所有来源域名 (浏览器H5模式调试)
        config.addAllowedOriginPattern("*");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许所有 HTTP 请求方法 (GET, POST, PUT, DELETE, OPTIONS等)
        config.addAllowedMethod("*");
        // 允许携带 Cookie / Token 凭证
        config.setAllowCredentials(true);
        // 预检请求缓存时间 (1小时内无需重复发 OPTIONS 预检)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有 /api/** 接口生效
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
