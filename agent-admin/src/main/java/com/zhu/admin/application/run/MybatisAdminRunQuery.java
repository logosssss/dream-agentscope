package com.zhu.admin.application.run;

import com.zhu.admin.api.run.AdminRunQuery;
import com.zhu.admin.api.run.RunDetail;
import com.zhu.admin.api.run.RunRow;
import com.zhu.admin.api.run.RunStepRow;
import com.zhu.ops.support.QueryLimits;
import com.zhu.readmodel.entity.run.AgentRun;
import com.zhu.readmodel.entity.run.AgentRunStep;
import com.zhu.readmodel.persistence.run.AgentRunMapper;
import com.zhu.readmodel.persistence.run.AgentRunStepMapper;
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

    public MybatisAdminRunQuery(AgentRunMapper runs, AgentRunStepMapper steps) {
        this.runs = runs;
        this.steps = steps;
    }

    /** 条数先收口，再交给 XML 里带条件的查询。 */
    @Override
    public List<RunRow> runs(String userId, String sessionId, String status, int limit) {
        List<AgentRun> rows = runs.search(
                blankToNull(userId), blankToNull(sessionId), blankToNull(status), QueryLimits.normalize(limit));
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
                stepRows);
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
