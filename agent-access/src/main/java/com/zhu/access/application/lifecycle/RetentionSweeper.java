package com.zhu.access.application.lifecycle;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按保留策略删超期数据。策略表由管理端维护，这里只读。
 *
 * <p>只执行 {@code action=DELETE}；ARCHIVE / ANONYMIZE 没有真实管线，登记了也不动数据。
 * 每张表一次最多删 {@code retention-batch-rows} 行，避免一条策略把大表锁住。
 *
 * <p>附件这里只删表行，不删对象存储里的字节（删除请求那条路径会连字节一起删）。
 * 这条口子写在文档的「明确不做」里，不装作已经处理。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class RetentionSweeper {

    private static final Logger log = Logger.getLogger(RetentionSweeper.class.getName());

    private static final int DEFAULT_BATCH = 1000;

    private final JdbcTemplate jdbc;

    private final LifecycleProperties properties;

    /** 只读策略表，不注入业务服务。 */
    public RetentionSweeper(JdbcTemplate jdbc, LifecycleProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** 定时入口。开关关着时空转。 */
    @Scheduled(fixedDelayString = "${agentscope.lifecycle.retention-interval-millis:300000}",
            initialDelayString = "${agentscope.lifecycle.initial-delay-millis:20000}")
    public void scheduled() {
        if (!properties.isRetentionEnabled()) {
            return;
        }
        try {
            Map<String, Integer> deleted = runOnce();
            if (!deleted.isEmpty()) {
                log.info("retention swept " + deleted);
            }
        } catch (RuntimeException ex) {
            log.warning("retention sweep failed");
        }
    }

    /**
     * 扫一遍全部启用中的 DELETE 策略，返回每张表删掉的行数。测试直接调它。
     */
    public Map<String, Integer> runOnce() {
        List<Map<String, Object>> policies = jdbc.queryForList(
                "SELECT data_type, scope, retain_days FROM data_retention_policy"
                        + " WHERE enabled = 1 AND action = 'DELETE'");
        Map<String, Integer> deleted = new LinkedHashMap<>();
        int batch = properties.getRetentionBatchRows() > 0
                ? properties.getRetentionBatchRows() : DEFAULT_BATCH;
        for (Map<String, Object> policy : policies) {
            String dataType = text(policy, "data_type");
            RetentionTargets.Target target = RetentionTargets.of(dataType).orElse(null);
            if (target == null) {
                continue;
            }
            int days = intValue(policy, "retain_days");
            if (days < 0) {
                continue;
            }
            String scope = text(policy, "scope");
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(days);
            boolean scoped = target.tenantScoped() && scope != null && !scope.isBlank() && !scope.equals("*");
            String sql = "DELETE FROM " + target.table()
                    + " WHERE " + target.timeColumn() + " < ?"
                    + (scoped ? " AND tenant_id = ?" : "")
                    + " LIMIT " + batch;
            int rows = scoped
                    ? jdbc.update(sql, cutoff, scope)
                    : jdbc.update(sql, cutoff);
            if (rows > 0) {
                deleted.merge(target.table(), rows, Integer::sum);
            }
        }
        return deleted;
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static int intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.intValue() : -1;
    }
}
