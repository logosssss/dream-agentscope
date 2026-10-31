package com.zhu.bootstrap;

import com.zhu.access.application.RedisReadiness;
import com.zhu.audit.application.MysqlReadiness;
import com.zhu.common.health.MilvusProbe;
import com.zhu.common.health.ReadinessSnapshot;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 用户进程的启动门槛。Redis、MySQL、Milvus 必须都能连上，否则退出。
 * 顺序在建集合之前。测试把开关关掉，不在这里连真实服务。
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class RequiredInfrastructureRunner implements ApplicationRunner {

    private final DataSource dataSource;

    private final RedisConnectionFactory redis;

    private final String milvusUri;

    /**
     * 三个依赖都由容器提供。Milvus 地址为空时探测会失败。
     */
    public RequiredInfrastructureRunner(
            DataSource dataSource,
            RedisConnectionFactory redis,
            @Value("${agentscope.milvus.uri:}") String milvusUri) {
        this.dataSource = dataSource;
        this.redis = redis;
        this.milvusUri = milvusUri;
    }

    /** 任一不可用就拒绝启动。不降级到进程内存。 */
    @Override
    public void run(ApplicationArguments args) {
        if (!ReadinessSnapshot.UP.equals(MysqlReadiness.status(true, dataSource))) {
            throw new IllegalStateException("mysql is required");
        }
        if (!ReadinessSnapshot.UP.equals(RedisReadiness.status(true, redis))) {
            throw new IllegalStateException("redis is required");
        }
        if (!MilvusProbe.reachable(milvusUri, 3000)) {
            throw new IllegalStateException("milvus is required");
        }
    }
}
