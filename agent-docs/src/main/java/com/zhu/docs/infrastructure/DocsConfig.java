package com.zhu.docs.infrastructure;

import com.zhu.docs.application.DocsProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档索引的 Spring 装配。Mapper 只在 MySQL 打开时扫描，没有数据源时不建 SqlSession。
 */
@Configuration
@EnableConfigurationProperties(DocsProperties.class)
public class DocsConfig {
}

/**
 * Mapper 扫描与配置拆开。MySQL 关掉时不能扫 Mapper，否则没有 SqlSessionFactory。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
@MapperScan("com.zhu.docs.infrastructure.persistence")
class DocsMapperConfig {
}
