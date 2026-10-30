package com.zhu.admin.application;

import com.zhu.audit.application.MysqlReadiness;
import com.zhu.common.health.MilvusProbe;
import com.zhu.common.health.ReadinessSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * 管理进程的就绪汇总。生产和用户进程一样探测 MySQL、Redis、Milvus。
 * 测试关掉启动开关后三项都跳过。
 */
@Service
public class AdminReadinessService {

    private static final Logger log = Logger.getLogger(AdminReadinessService.class.getName());

    private final boolean required;

    private final String milvusUri;

    private final ObjectProvider<DataSource> dataSources;

    private final ObjectProvider<RedisConnectionFactory> redis;

    /**
     * 开关缺省为打开。测试配置改成关闭。
     */
    public AdminReadinessService(
            @Value("${agentscope.startup.dependencies.enabled:true}") boolean required,
            @Value("${agentscope.milvus.uri:}") String milvusUri,
            ObjectProvider<DataSource> dataSources,
            ObjectProvider<RedisConnectionFactory> redis) {
        this.required = required;
        this.milvusUri = milvusUri;
        this.dataSources = dataSources;
        this.redis = redis;
    }

    /** 测试里三项都是 SKIPPED。生产有一项 DOWN 时总状态为 DOWN。 */
    public ReadinessSnapshot snapshot() {
        Map<String, String> checks = new LinkedHashMap<>();
        if (!required) {
            checks.put("mysql", ReadinessSnapshot.SKIPPED);
            checks.put("redis", ReadinessSnapshot.SKIPPED);
            checks.put("milvus", ReadinessSnapshot.SKIPPED);
            return ReadinessSnapshot.of(checks);
        }
        checks.put("mysql", MysqlReadiness.status(true, dataSources.getIfAvailable()));
        checks.put("redis", redis());
        checks.put("milvus", MilvusProbe.reachable(milvusUri, 3000)
                ? ReadinessSnapshot.UP
                : ReadinessSnapshot.DOWN);
        return ReadinessSnapshot.of(checks);
    }

    /** 没有连接工厂或 PING 失败都是 DOWN。 */
    private String redis() {
        RedisConnectionFactory factory = redis.getIfAvailable();
        if (factory == null) {
            return ReadinessSnapshot.DOWN;
        }
        try (RedisConnection connection = factory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping())
                    ? ReadinessSnapshot.UP
                    : ReadinessSnapshot.DOWN;
        } catch (RuntimeException ex) {
            log.warning("redis readiness check failed");
            return ReadinessSnapshot.DOWN;
        }
    }
}
