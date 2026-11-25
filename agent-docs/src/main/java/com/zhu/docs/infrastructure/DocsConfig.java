package com.zhu.docs.infrastructure;

import com.zhu.docs.application.DocsProperties;
import com.zhu.docs.application.DocsSourceMerge;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档索引的 Spring 装配。Mapper 只在 MySQL 打开时扫描，没有数据源时不建 SqlSession。
 */
@Configuration
@EnableConfigurationProperties(DocsProperties.class)
public class DocsConfig {

    /**
     * 环境变量只写了 root 时，Spring 会把这一条源整段换掉，仓库名和收录规则变空，同步扫到 0 篇。
     * 空字段用包内默认补上。环境变量里写了的非空字段仍优先。
     */
    @Autowired
    void keepPackagedSourceRules(DocsProperties properties) {
        properties.setSources(DocsSourceMerge.merge(properties.getSources()));
    }
}

/**
 * Mapper 扫描与配置拆开。MySQL 关掉时不能扫 Mapper，否则没有 SqlSessionFactory。
 */
@Configuration
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
@MapperScan("com.zhu.docs.infrastructure.persistence")
class DocsMapperConfig {
}
