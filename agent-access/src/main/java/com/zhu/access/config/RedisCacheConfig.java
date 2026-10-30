package com.zhu.access.config;

import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.access.infrastructure.cache.RedisAccessCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 只有显式打开并且写了主机时才连 Redis。自动配置已在启动模块排除，避免默认去连本机。
 */
@Configuration
@ConditionalOnProperty(prefix = "agentscope.cache.redis", name = "enabled", havingValue = "true")
public class RedisCacheConfig {

    /**
     * 主机为空就拒绝启动。端口可以用默认值，地址不能猜。
     */
    @Bean
    public LettuceConnectionFactory accessRedisConnectionFactory(RedisCacheProperties properties) {
        if (properties.getHost() == null || properties.getHost().isBlank()) {
            throw new IllegalStateException("agentscope.cache.redis.host is required when redis is enabled");
        }
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(properties.getPassword()));
        }
        return new LettuceConnectionFactory(standalone);
    }

    /** 额度计数只存数字和占位标记，用字符串模板即可。 */
    @Bean
    public StringRedisTemplate accessRedisTemplate(LettuceConnectionFactory accessRedisConnectionFactory) {
        return new StringRedisTemplate(accessRedisConnectionFactory);
    }

    /** 额度计数只走 Redis。测试关掉开关后由测试替身提供 AccessCache。 */
    @Bean
    public AccessCache redisAccessCache(StringRedisTemplate accessRedisTemplate) {
        return new RedisAccessCache(accessRedisTemplate);
    }
}
