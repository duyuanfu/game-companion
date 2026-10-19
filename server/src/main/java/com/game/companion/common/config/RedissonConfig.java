package com.game.companion.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁与客户端配置
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;
        
        var singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setTimeout(3000);

        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            singleServerConfig.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}
