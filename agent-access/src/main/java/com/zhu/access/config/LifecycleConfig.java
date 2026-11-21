package com.zhu.access.config;

import com.zhu.access.application.lifecycle.LifecycleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 数据生命周期的配置绑定。开关与 dry-run 都在 {@link LifecycleProperties} 上，
 * 执行器本身按 MySQL 开关装配，不在两个进程里各写一份。
 */
@Configuration
@EnableConfigurationProperties(LifecycleProperties.class)
public class LifecycleConfig {
}
