package com.zhu.access.application;

import com.zhu.access.api.dto.TaskDetail;
import com.zhu.access.api.dto.TaskSummary;
import com.zhu.access.infrastructure.persistence.AgentTaskMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentTaskRow;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按用户和租户列出同步任务，详情再带开始结束时间。只在存储开关打开时创建。不读任务参数。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTaskQuery implements TaskQuery {

    private static final int USER_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final int TASK_ID_LIMIT = 128;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AgentTaskMapper tasks;

    /** 列表用 Mapper。空结果返回空列表，不抛找不到。 */
    public MysqlTaskQuery(AgentTaskMapper tasks) {
        this.tasks = tasks;
    }

    /**
     * 空用户拒绝。空白租户按空串，和写入时一致。
     */
    @Override
    public List<TaskSummary> owned(String userId, String tenantId, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task query is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        List<AgentTaskRow> rows = tasks.listOwned(userId, savedTenant, normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlTaskQuery::summary).toList();
    }

    /**
     * 空标识拒绝。找不到或不是本人按找不到，避免把别人的任务露出去。
     */
    @Override
    public TaskDetail ownedOne(String userId, String tenantId, String taskId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || taskId == null || taskId.isBlank() || taskId.length() > TASK_ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task query is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        AgentTaskRow row = tasks.findOwned(userId, savedTenant, taskId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "task not found");
        }
        return detail(row);
    }

    /** 列表不带回开始结束时间，也不带回 payload。 */
    private static TaskSummary summary(AgentTaskRow row) {
        String tenant = row.getTenantId() == null ? "" : row.getTenantId();
        return new TaskSummary(
                row.getTaskId(),
                tenant,
                row.getUserId(),
                row.getSessionId(),
                row.getTaskType(),
                row.getStatus(),
                row.getErrorMessage());
    }

    /** 详情补开始结束时间。进行中的任务结束时间为空串。 */
    private static TaskDetail detail(AgentTaskRow row) {
        String tenant = row.getTenantId() == null ? "" : row.getTenantId();
        String started = row.getStartedAt() == null ? "" : TIME.format(row.getStartedAt());
        String finished = row.getFinishedAt() == null ? "" : TIME.format(row.getFinishedAt());
        return new TaskDetail(
                row.getTaskId(),
                tenant,
                row.getUserId(),
                row.getSessionId(),
                row.getTaskType(),
                row.getStatus(),
                row.getErrorMessage(),
                started,
                finished);
    }

    /** 未指定时用 50，调用方传得再大也只取 100。 */
    private static int normalize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}
