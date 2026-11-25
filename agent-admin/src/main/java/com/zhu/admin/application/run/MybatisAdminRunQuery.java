package com.zhu.admin.application.run;

import com.zhu.admin.api.run.AdminRunQuery;
import com.zhu.admin.api.run.RunDetail;
import com.zhu.admin.api.run.RunRow;
import com.zhu.admin.api.run.RunStepRow;
import com.zhu.admin.api.run.RunUsage;
import com.zhu.ops.support.QueryLimits;
import com.zhu.readmodel.entity.run.AgentRun;
import com.zhu.readmodel.entity.run.AgentRunStep;
import com.zhu.readmodel.persistence.run.AgentRunMapper;
import com.zhu.readmodel.persistence.run.AgentRunStepMapper;
import com.zhu.readmodel.persistence.usage.RunUsageTotal;
import com.zhu.readmodel.persistence.usage.TokenUsageMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 从 MySQL 读运行记录。只在存储开关打开时创建。
 *
 * <p>管理端看的是「别人的失败」，所以这里不按用户或租户收口——权限由 Admin Token 把住。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisAdminRunQuery implements AdminRunQuery {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AgentRunMapper runs;
    private final AgentRunStepMapper steps;
    private final TokenUsageMapper usages;

    /** 用量和运行分表。详情才按 run_id 加总，列表不查 token。 */
    public MybatisAdminRunQuery(AgentRunMapper runs, AgentRunStepMapper steps, TokenUsageMapper usages) {
        this.runs = runs;
        this.steps = steps;
        this.usages = usages;
    }

    /** 条数先收口，再交给 XML 里带条件的查询。空白请求号不当成要匹配的空串。 */
    @Override
    public List<RunRow> runs(String requestId, String userId, String sessionId, String status, int limit) {
        List<AgentRun> rows = runs.search(
                blankToNull(requestId),
                blankToNull(userId),
                blankToNull(sessionId),
                blankToNull(status),
                QueryLimits.normalize(limit));
        List<RunRow> result = new ArrayList<>(rows.size());
        for (AgentRun row : rows) {
            result.add(RunRow.of(row, time(row.getStartedAt()), time(row.getFinishedAt())));
        }
        return result;
    }

    /** 步骤单独查：列表页不需要，详情页才要。 */
    @Override
    public RunDetail run(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        AgentRun row = runs.findByRunId(runId.trim());
        if (row == null) {
            return null;
        }
        List<RunStepRow> stepRows = new ArrayList<>();
        for (AgentRunStep step : steps.listByRunId(row.getRunId())) {
            stepRows.add(RunStepRow.of(step));
        }
        return new RunDetail(
                row.getRunId(),
                row.getTenantId(),
                row.getUserId(),
                row.getSessionId(),
                row.getRequestId() == null ? "" : row.getRequestId(),
                row.getTriggerType(),
                row.getStatus(),
                row.getModel(),
                row.getInputSummary(),
                row.getOutputSummary(),
                row.getErrorCode(),
                row.getDurationMs(),
                time(row.getStartedAt()),
                time(row.getFinishedAt()),
                stepRows,
                usageOf(row.getRunId()));
    }

    /**
     * 同一轮多次调用加总。没有行给未记录，避免把「没记用量」显示成花了 0 token。
     * 多种币种时币种留空，金额仍给出，不把不同币种说成一种。
     */
    private RunUsage usageOf(String runId) {
        RunUsageTotal total = usages.sumByRunId(runId);
        if (total == null || total.getRowCount() == null || total.getRowCount() == 0) {
            return RunUsage.none();
        }
        String currency = "";
        if (total.getCurrencyCount() != null && total.getCurrencyCount() == 1 && total.getCurrency() != null) {
            currency = total.getCurrency();
        }
        BigDecimal amount = total.getCostAmount() == null ? BigDecimal.ZERO : total.getCostAmount();
        return new RunUsage(
                longOrZero(total.getInputTokens()),
                longOrZero(total.getOutputTokens()),
                longOrZero(total.getTotalTokens()),
                amount,
                currency,
                true);
    }

    /** 合计列为空时按 0，不把 null 传给前端。 */
    private static long longOrZero(Long value) {
        return value == null ? 0L : value;
    }

    /** 空串当「没有这个条件」，避免 XML 里把 '' 当成要匹配的值。 */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 时间统一成 ISO 字符串，空值给空串，前端不用判 null。 */
    private static String time(LocalDateTime value) {
        return value == null ? "" : value.format(ISO);
    }
}
