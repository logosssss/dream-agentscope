package com.zhu.access.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 只在 MySQL 打开时扫描会话 Mapper。没有数据源时不创建 SqlSession。
 * 不注册 DDL 执行器，表结构只由用户进程的 Flyway 变更。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
@MapperScan("com.zhu.access.infrastructure.persistence")
public class SessionMapperConfig {
}
