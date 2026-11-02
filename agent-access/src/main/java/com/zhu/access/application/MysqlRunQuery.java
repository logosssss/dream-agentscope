package com.zhu.access.application;

import com.zhu.access.api.dto.RunSummary;
import com.zhu.access.infrastructure.persistence.AgentRunMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentRun;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按用户和租户列出已经结束的运行。只在存储开关打开时创建。不读步骤。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlRunQuery implements RunQuery {

    private static final int USER_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private final AgentRunMapper runs;

    /** 列表用 Mapper。空结果返回空列表，不抛找不到。 */
    public MysqlRunQuery(AgentRunMapper runs) {
        this.runs = runs;
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

    /** 未指定时用 50，调用方传得再大也只取 100。 */
    private static int normalize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}
