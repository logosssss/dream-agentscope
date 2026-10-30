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

@Configuration
@EnableConfigurationProperties(MysqlStoreProperties.class)
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class DistributedStoreConfig {

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
