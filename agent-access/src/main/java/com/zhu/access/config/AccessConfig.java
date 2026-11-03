package com.zhu.access.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 打开接入层额度和 Redis 开关的配置绑定。连接本身由 RedisCacheConfig 按开关创建。
 */
@Configuration
@EnableConfigurationProperties({
        AccessLimitProperties.class,
        RedisCacheProperties.class,
        ClientAuthProperties.class,
        WxMiniProperties.class
})
public class AccessConfig {
}
