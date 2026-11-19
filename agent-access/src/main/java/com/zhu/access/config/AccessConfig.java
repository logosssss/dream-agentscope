package com.zhu.access.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 打开接入层额度与认证相关配置绑定。
 * Redis 连接与 {@code CacheFacade} 由 {@code agent-cache-redis} 自动配置提供。
 */
@Configuration
@EnableConfigurationProperties({
        AccessLimitProperties.class,
        ClientAuthProperties.class,
        WxMiniProperties.class
})
public class AccessConfig {
}
