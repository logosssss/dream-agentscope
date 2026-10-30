package com.zhu.audit.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 只在 MySQL 打开时扫描审计 Mapper。没有数据源时不创建 SqlSession。
 * 不注册 DDL 执行器，表结构只由 Flyway 变更。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
@MapperScan("com.zhu.audit.infrastructure.persistence")
public class AuditMapperConfig {
}
