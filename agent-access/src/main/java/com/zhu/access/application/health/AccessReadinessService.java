package com.zhu.access.application.health;

import com.zhu.audit.application.health.MysqlReadiness;
import com.zhu.common.health.MilvusProbe;
import com.zhu.common.health.ReadinessSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * 用户进程的就绪汇总。生产必须探测 MySQL、Redis 和 Milvus。
 * 测试关掉启动开关后三项都跳过，不去连真实服务。
 */
@Service
public class AccessReadinessService {

    private final boolean required;

    private final String milvusUri;

    private final ObjectProvider<DataSource> dataSources;

    private final ObjectProvider<RedisConnectionFactory> redis;

    /**
     * 开关缺省为打开。测试配置改成关闭。
     */
    public AccessReadinessService(
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
        checks.put("redis", RedisReadiness.status(true, redis.getIfAvailable()));
        checks.put("milvus", MilvusProbe.reachable(milvusUri, 3000)
                ? ReadinessSnapshot.UP
                : ReadinessSnapshot.DOWN);
        return ReadinessSnapshot.of(checks);
    }
}
