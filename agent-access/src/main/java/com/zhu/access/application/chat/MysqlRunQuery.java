package com.zhu.access.application.chat;

import com.zhu.access.api.dto.RunDetail;
import com.zhu.access.api.dto.RunStepSummary;
import com.zhu.access.api.dto.RunSummary;
import com.zhu.access.infrastructure.persistence.AgentRunMapper;
import com.zhu.access.infrastructure.persistence.AgentRunStepMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentRun;
import com.zhu.access.infrastructure.persistence.entity.AgentRunStep;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按用户和租户读已经结束的运行。只在存储开关打开时创建。
 * 列表不读步骤；详情先确认归属再读步骤。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlRunQuery implements RunQuery {

    private static final int USER_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final int RUN_ID_LIMIT = 128;

    /** 开始和结束时间按本地日期时间写出，不带时区后缀。 */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AgentRunMapper runs;

    private final AgentRunStepMapper steps;

    /** 列表和详情共用运行表。步骤表只在确认归属后读。 */
    public MysqlRunQuery(AgentRunMapper runs, AgentRunStepMapper steps) {
        this.runs = runs;
        this.steps = steps;
    }

    /**
     * 空用户拒绝。空白租户按空串，和写入时一致。
     */
    @Override
    public List<RunSummary> owned(String userId, String tenantId, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "run query is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        List<AgentRun> rows = runs.listOwned(userId, savedTenant, normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlRunQuery::summary).toList();
    }

    /**
     * 空标识拒绝。找不到或不是本人按找不到，避免把别人的步骤露出去。
     * 带开始和结束时间。不返回异常原文。
     */
    @Override
    public RunDetail ownedOne(String userId, String tenantId, String runId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || runId == null || runId.isBlank() || runId.length() > RUN_ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "run query is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        AgentRun row = runs.findOwned(userId, savedTenant, runId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "run not found");
        }
        List<AgentRunStep> recorded = steps.listByRunId(row.getRunId());
        String started = row.getStartedAt() == null ? "" : TIME.format(row.getStartedAt());
        String finished = row.getFinishedAt() == null ? "" : TIME.format(row.getFinishedAt());
        return new RunDetail(summary(row), stepSummaries(recorded), started, finished);
    }

    /** 列表不带回开始结束时间，调用方用耗时即可。 */
    private static RunSummary summary(AgentRun row) {
        String tenant = row.getTenantId() == null ? "" : row.getTenantId();
        return new RunSummary(
                row.getRunId(),
                tenant,
                row.getUserId(),
                row.getSessionId(),
                row.getTriggerType(),
                row.getStatus(),
                row.getModel(),
                row.getInputSummary(),
                row.getOutputSummary(),
                row.getErrorCode(),
                row.getDurationMs());
    }

    /** 丢掉没有序号的行。耗时和 token 写入时就是空的，这里不补。 */
    private static List<RunStepSummary> stepSummaries(List<AgentRunStep> recorded) {
        if (recorded == null || recorded.isEmpty()) {
            return List.of();
        }
        List<RunStepSummary> rows = new ArrayList<>();
        for (AgentRunStep row : recorded) {
            if (row == null || row.getStepNo() == null) {
                continue;
            }
            rows.add(new RunStepSummary(
                    row.getStepNo(),
                    row.getStepType(),
                    row.getToolId(),
                    row.getStatus()));
        }
        return List.copyOf(rows);
    }

    /** 未指定时用 50，调用方传得再大也只取 100。 */
    private static int normalize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}
