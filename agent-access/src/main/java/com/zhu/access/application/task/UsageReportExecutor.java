package com.zhu.access.application.task;

import com.zhu.common.util.UtcTimes;
import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.handle.AgentTaskExecutor;
import com.zhu.runtime.task.handle.TaskPayload;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * REPORT：按天汇总一个用户最近一段时间的用量，写进审计。
 *
 * <p>为什么用任务而不是接口：报表是「事后算、可重跑」的东西，放在定时任务里算完就留痕，
 * 比让前端每次点开都扫一遍表更稳，也不会把慢查询压在用户请求上。
 *
 * <p>只读三张表：{@code token_usage}（token 与金额）、{@code agent_run}（运行次数）、
 * {@code chat_message}（消息条数）。不带提示词、不带回复正文，审计 detail 里只有数字。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class UsageReportExecutor implements AgentTaskExecutor {

    /** 任务类型。调度表里 task_type 填这个名字。 */
    public static final String TYPE = "REPORT";

    private static final int DEFAULT_DAYS = 7;

    private static final int MAX_DAYS = 90;

    private static final String TOKEN_SUM_SQL =
            "SELECT COALESCE(SUM(total_tokens), 0) FROM token_usage"
                    + " WHERE tenant_id = ? AND user_id = ? AND created_at >= ?";

    private static final String COST_SUM_SQL =
            "SELECT COALESCE(SUM(cost_amount), 0) FROM token_usage"
                    + " WHERE tenant_id = ? AND user_id = ? AND created_at >= ?";

    private static final String RUN_COUNT_SQL =
            "SELECT COUNT(*) FROM agent_run WHERE tenant_id = ? AND user_id = ? AND created_at >= ?";

    private static final String MESSAGE_COUNT_SQL =
            "SELECT COUNT(*) FROM chat_message WHERE tenant_id = ? AND user_id = ? AND created_at >= ?";

    private final JdbcTemplate jdbc;

    private final AuditRecorder audit;

    public UsageReportExecutor(JdbcTemplate jdbc, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * payload 可选：{@code days} 回溯天数（默认 7，上限 90，至少 1）。
     */
    @Override
    public void execute(AgentTask task) {
        String tenantId = task.tenantId() == null ? "" : task.tenantId();
        String userId = task.userId();
        int days = TaskPayload.bounded(task.payload(), "days", DEFAULT_DAYS, 1, MAX_DAYS);
        LocalDateTime since = UtcTimes.now().minusDays(days);
        long tokens = number(TOKEN_SUM_SQL, tenantId, userId, since).longValue();
        BigDecimal cost = decimal(COST_SUM_SQL, tenantId, userId, since);
        long runs = number(RUN_COUNT_SQL, tenantId, userId, since).longValue();
        long messages = number(MESSAGE_COUNT_SQL, tenantId, userId, since).longValue();
        // 金额保留 6 位小数，与表里的 DECIMAL(14,6) 对齐，避免审计里出现 0E-6 这种写法。
        audit.record(
                "task.report",
                userId,
                "days=" + days
                        + ",tokens=" + tokens
                        + ",cost=" + cost.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString()
                        + ",runs=" + runs
                        + ",messages=" + messages);
    }

    /** 聚合查询一定返回一行，驱动若给 null 就按 0 算。 */
    private Number number(String sql, String tenantId, String userId, LocalDateTime since) {
        Number value = jdbc.queryForObject(sql, Number.class, tenantId, userId, since);
        return value == null ? 0L : value;
    }

    private BigDecimal decimal(String sql, String tenantId, String userId, LocalDateTime since) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, tenantId, userId, since);
        return value == null ? BigDecimal.ZERO : value;
    }
}
