package com.zhu.state.config;

import io.agentscope.extensions.mysql.sandbox.JdbcSandboxExecutionGuard;
import io.agentscope.extensions.mysql.snapshot.JdbcSnapshotSpec;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.extensions.mysql.store.JdbcStore;
import io.agentscope.harness.agent.DistributedStore;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把会话状态、工作区文件、沙箱快照和执行锁都接到 MySQL。
 * 框架自带的一行工厂会默认建表，这里按配置关掉自动建表，改由 Flyway 管 DDL。
 */
@Configuration
@EnableConfigurationProperties(MysqlStoreProperties.class)
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class DistributedStoreConfig {

    /**
     * 四个存储组件共用同一个数据源。未打开开关时这个 Bean 不存在。
     */
    @Bean
    public DistributedStore distributedStore(DataSource dataSource, MysqlStoreProperties properties) {
        boolean create = properties.isCreateIfNotExist();
        return DistributedStore.builder()
                .agentStateStore(new MysqlAgentStateStore(dataSource, create))
                .baseStore(JdbcStore.builder(dataSource).initializeSchema(create).build())
                .sandboxSnapshotSpec(new JdbcSnapshotSpec(dataSource))
                .sandboxExecutionGuard(JdbcSandboxExecutionGuard.builder(dataSource).build())
                .build();
    }
}
