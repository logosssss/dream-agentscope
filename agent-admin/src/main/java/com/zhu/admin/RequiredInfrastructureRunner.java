package com.zhu.admin;

import com.zhu.audit.application.health.MysqlReadiness;
import com.zhu.common.health.MilvusProbe;
import com.zhu.common.health.ReadinessSnapshot;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 管理进程的启动门槛。连不上 Redis、MySQL 或 Milvus 就退出。
 * 本进程不跑 Flyway。测试关掉开关，不连真实服务。
 */
@Component
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class RequiredInfrastructureRunner implements ApplicationRunner {

    private static final Logger log = Logger.getLogger(RequiredInfrastructureRunner.class.getName());

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

    /** 任一不可用就拒绝启动。管理进程不建表。 */
    @Override
    public void run(ApplicationArguments args) {
        if (!ReadinessSnapshot.UP.equals(MysqlReadiness.status(true, dataSource))) {
            throw new IllegalStateException("mysql is required");
        }
        if (!pong(redis)) {
            throw new IllegalStateException("redis is required");
        }
        if (!MilvusProbe.reachable(milvusUri, 3000)) {
            throw new IllegalStateException("milvus is required");
        }
    }

    /** PING 必须是 PONG。失败时不把异常原文打出来。 */
    private static boolean pong(RedisConnectionFactory factory) {
        try (RedisConnection connection = factory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException ex) {
            log.warning("redis is not reachable");
            return false;
        }
    }
}
