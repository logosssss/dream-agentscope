package com.zhu.admin.application;

import com.zhu.audit.application.MysqlReadiness;
import com.zhu.common.health.ReadinessSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 管理进程的就绪汇总。这个进程不用 Redis，只看 MySQL 开关。
 * 开关关闭时查询接口本来就返回空列表，进程可以接管理流量。
 */
@Service
public class AdminReadinessService {

    private final boolean mysqlEnabled;

    private final ObjectProvider<DataSource> dataSources;

    /**
     * 开关缺省按关闭处理。没有数据源时，只有开关打开才算未就绪。
     */
    public AdminReadinessService(
            @Value("${agentscope.distributed.mysql.enabled:false}") boolean mysqlEnabled,
            ObjectProvider<DataSource> dataSources) {
        this.mysqlEnabled = mysqlEnabled;
        this.dataSources = dataSources;
    }

    /** MySQL 未启用时总状态为 UP。 */
    public ReadinessSnapshot snapshot() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("mysql", MysqlReadiness.status(mysqlEnabled, dataSources.getIfAvailable()));
        return ReadinessSnapshot.of(checks);
    }
}
