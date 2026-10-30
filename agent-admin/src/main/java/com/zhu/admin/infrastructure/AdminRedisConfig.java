package com.zhu.admin.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 管理进程只为启动探测连接 Redis。不在这里做额度计数。
 * 主机来自环境变量，为空就拒绝启动，不猜本机地址。
 */
@Configuration
@ConditionalOnProperty(prefix = "agentscope.cache.redis", name = "enabled", havingValue = "true")
public class AdminRedisConfig {

    /**
     * 主机必填。端口可以走默认值。
     */
    @Bean
    public LettuceConnectionFactory adminRedisConnectionFactory(
            @Value("${agentscope.cache.redis.host:}") String host,
            @Value("${agentscope.cache.redis.port:6379}") int port,
            @Value("${agentscope.cache.redis.password:}") String password) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("agentscope.cache.redis.host is required");
        }
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            standalone.setPassword(RedisPassword.of(password));
        }
        return new LettuceConnectionFactory(standalone);
    }
}
