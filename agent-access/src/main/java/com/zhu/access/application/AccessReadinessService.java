package com.zhu.access.application;

import com.zhu.audit.application.MysqlReadiness;
import com.zhu.common.health.ReadinessSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * 用户进程的就绪汇总。MySQL 和 Redis 默认关闭，关闭时不探测。
 * 向量库还没有接入，不放进这次检查。
 */
@Service
public class AccessReadinessService {

    private final boolean mysqlEnabled;

    private final boolean redisEnabled;

    private final ObjectProvider<DataSource> dataSources;

    private final ObjectProvider<RedisConnectionFactory> redis;

    /**
     * 开关缺省按关闭处理，和进程内实现一致。数据源和 Redis 工厂可以不存在。
     */
    public AccessReadinessService(
            @Value("${agentscope.distributed.mysql.enabled:false}") boolean mysqlEnabled,
            @Value("${agentscope.cache.redis.enabled:false}") boolean redisEnabled,
            ObjectProvider<DataSource> dataSources,
            ObjectProvider<RedisConnectionFactory> redis) {
        this.mysqlEnabled = mysqlEnabled;
        this.redisEnabled = redisEnabled;
        this.dataSources = dataSources;
        this.redis = redis;
    }

    /** 两项都不是 DOWN 时总状态为 UP。 */
    public ReadinessSnapshot snapshot() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("mysql", MysqlReadiness.status(mysqlEnabled, dataSources.getIfAvailable()));
        checks.put("redis", RedisReadiness.status(redisEnabled, redis.getIfAvailable()));
        return ReadinessSnapshot.of(checks);
    }
}
