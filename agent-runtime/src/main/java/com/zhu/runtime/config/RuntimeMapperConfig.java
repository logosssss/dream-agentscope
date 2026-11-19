package com.zhu.runtime.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 运行层 Mapper 扫描。只在 MySQL 打开时生效。
 * 覆盖事件审计写入、token 用量、安全干预、模型查找与计价只读。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
@MapperScan("com.zhu.runtime.infrastructure.persistence")
public class RuntimeMapperConfig {
}
