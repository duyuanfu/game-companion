package com.game.companion.common.security;

import cn.hutool.crypto.SecureUtil;
import com.game.companion.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 登录/注册基础限流。
 *
 * <p>使用 Redis 计数器做手机号 + IP 双维度失败限制：
 * 同一手机号 15 分钟内失败 5 次、同一 IP 15 分钟内失败 30 次即暂时拒绝。
 * Redis 不可用时降级放行，避免登录功能被缓存故障拖死。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final int PHONE_MAX_FAILURES = 5;
    private static final int IP_MAX_FAILURES = 30;
    private static final int REGISTER_MAX_PER_HOUR = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public void ensureLoginAllowed(String phone) {
        String phoneKey = phoneKey(phone);
        String ipKey = ipKey();
        if (count(phoneKey) >= PHONE_MAX_FAILURES) {
            throw new ApiException("登录失败次数过多，请 15 分钟后再试");
        }
        if (count(ipKey) >= IP_MAX_FAILURES) {
            throw new ApiException("当前网络登录尝试过于频繁，请稍后再试");
        }
    }

    public void recordLoginFailure(String phone) {
        increment(phoneKey(phone), LOGIN_WINDOW);
        increment(ipKey(), LOGIN_WINDOW);
    }

    public void clearLoginFailures(String phone) {
        try {
            redisTemplate.delete(phoneKey(phone));
        } catch (Exception e) {
            log.debug("清理登录失败计数失败: {}", e.getMessage());
        }
    }

    public void ensureRegisterAllowed() {
        String key = "auth:register:ip:" + clientIp();
        if (count(key) >= REGISTER_MAX_PER_HOUR) {
            throw new ApiException("注册过于频繁，请稍后再试");
        }
        increment(key, REGISTER_WINDOW);
    }

    private long count(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception e) {
            log.debug("读取限流计数失败，降级放行: {}", e.getMessage());
            return 0;
        }
    }

    private void increment(String key, Duration ttl) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception e) {
            log.debug("写入限流计数失败，降级放行: {}", e.getMessage());
        }
    }

    private String phoneKey(String phone) {
        return "auth:login:phone:" + SecureUtil.md5(phone == null ? "" : phone.trim());
    }

    private String ipKey() {
        return "auth:login:ip:" + clientIp();
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
