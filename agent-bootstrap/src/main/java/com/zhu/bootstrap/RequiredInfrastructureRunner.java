package com.zhu.bootstrap;

import com.zhu.access.application.health.RedisReadiness;
import com.zhu.ops.health.MysqlReadiness;
import com.zhu.common.health.MilvusProbe;
import com.zhu.common.health.ReadinessSnapshot;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 用户进程的启动门槛。MySQL、Flyway、Redis、Milvus 必须都在，否则退出。
 * 顺序在建集合之前。测试把开关关掉，不在这里连真实服务。
 * 管理进程没有这个类，仍排除 Flyway。
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class RequiredInfrastructureRunner implements ApplicationRunner {

    private final DataSource dataSource;

    private final ObjectProvider<Flyway> flyway;

    private final RedisConnectionFactory redis;

    private final String milvusUri;

    /**
     * MySQL、Redis、Milvus 由容器提供。Milvus 地址为空时探测会失败。
     * Flyway 用 Provider：排除自动配置或 {@code spring.flyway.enabled=false} 时没有 Bean，
     * 不能写成构造器硬依赖，否则日志只剩一长串依赖缺失。
     */
    public RequiredInfrastructureRunner(
            DataSource dataSource,
            ObjectProvider<Flyway> flyway,
            RedisConnectionFactory redis,
            @Value("${agentscope.milvus.uri:}") String milvusUri) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.redis = redis;
        this.milvusUri = milvusUri;
    }

    /**
     * 任一不可用就拒绝启动。不降级到进程内存。
     * Flyway 排在 MySQL 之后：库不通时先报库，库通了但迁移没装上再报 Flyway。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!ReadinessSnapshot.UP.equals(MysqlReadiness.status(true, dataSource))) {
            throw new IllegalStateException("mysql is required");
        }
        // 有 Bean 才说明自动配置跑过。迁移失败会在更早的初始化阶段退出，这里只拦「被悄悄关掉」。
        if (flyway.getIfAvailable() == null) {
            throw new IllegalStateException("flyway is required");
        }
        if (!ReadinessSnapshot.UP.equals(RedisReadiness.status(true, redis))) {
            throw new IllegalStateException("redis is required");
        }
        if (!MilvusProbe.reachable(milvusUri, 3000)) {
            throw new IllegalStateException("milvus is required");
        }
    }
}
