package com.game.companion.common.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 【全局登录态拦截器 - 默认拒绝】
 *
 * <p>采用「白名单放行 + 其余全部要求登录」的默认拒绝策略，
 * 避免新增接口时因忘记加注解而造成越权裸奔。</p>
 *
 * <p>未登录时返回 HTTP 200 + 业务码 401（与项目 {@code GlobalExceptionHandler} 的响应约定保持一致），
 * 前端 {@code utils/request.ts} 已内置 401 → 静默重登 → 原地重试的自愈机制。</p>
 */
@Configuration
public class AuthInterceptorConfig implements WebMvcConfigurer {

    /**
     * 无需登录即可访问的公开接口白名单。
     */
    public static final List<String> PUBLIC_ENDPOINTS = List.of(
            // 登录与开发期工具
            "/user/wx-login",
            "/user/login",
            "/user/register",
            // SSE 流在 Controller 内手动校验 token
            "/chat/stream",
            // 游戏品类字典
            "/game/**",
            // 陪玩师公开浏览（首页精选列表 / 技能定价）
            "/companion/list",
            "/companion/skills",
            // 多媒体流式直读（真机试听原声、战绩截图预览）
            "/file/download/**",
            // 服务自检（小程序端用于确认 BASE_URL 指向的后端身份是否正确）
            "/health",
            // 微信支付 V3 异步回调（由微信服务器调用，请求体经官方 RSA 验签 + AES-GCM 解密校验）
            "/pay/notify",
            // 错误页与接口文档
            "/error",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/favicon.ico"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginRequiredInterceptor())
                .addPathPatterns("/**");
    }

    /**
     * 登录态拦截器实现。
     */
    public static class LoginRequiredInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            // 预检请求直接放行
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            String path = resolvePath(request);
            if (isPublic(path)) {
                return true;
            }

            if (StpUtil.isLogin()) {
                return true;
            }

            // 未登录：按项目统一响应约定返回业务码 401，触发前端无感静默重登
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"code\":401,\"message\":\"未登录或登录状态已失效，正在自动重新登录\",\"data\":null}");
            return false;
        }

        /**
         * 剥离 context-path，得到应用内真实请求路径。
         */
        private String resolvePath(HttpServletRequest request) {
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                uri = uri.substring(contextPath.length());
            }
            if (uri.isEmpty()) {
                uri = "/";
            }
            return uri;
        }

        private boolean isPublic(String path) {
            for (String pattern : PUBLIC_ENDPOINTS) {
                if (PATH_MATCHER.match(pattern, path)) {
                    return true;
                }
            }
            return false;
        }
    }
}
