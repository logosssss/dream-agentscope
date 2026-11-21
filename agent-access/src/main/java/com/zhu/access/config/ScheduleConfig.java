package com.zhu.access.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 任务调度触发面的配置绑定。开关在 {@link ScheduleProperties} 上，
 * 触发逻辑本身按 MySQL 开关装配；管理进程只登记，不创建这个 Bean。
 */
@Configuration
@EnableConfigurationProperties(ScheduleProperties.class)
public class ScheduleConfig {
}
