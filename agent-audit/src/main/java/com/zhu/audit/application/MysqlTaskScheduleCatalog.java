package com.zhu.audit.application;

import com.zhu.audit.api.TaskScheduleCatalog;
import com.zhu.audit.api.TaskScheduleSummary;
import com.zhu.audit.infrastructure.persistence.AgentTaskScheduleMapper;
import com.zhu.audit.infrastructure.persistence.entity.AgentTaskSchedule;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记、停用、恢复和列出任务调度。只在存储开关打开时创建。不计算下次触发，也不消费。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTaskScheduleCatalog implements TaskScheduleCatalog {

    private static final int ID_LIMIT = 128;

    private static final int TYPE_LIMIT = 64;

    private static final int CRON_LIMIT = 128;

    private static final Set<String> TASK_TYPES = Set.of("BACKFILL", "REPORT", "BATCH");

    private final AgentTaskScheduleMapper schedules;

    /** 同一标识由唯一键拒绝。 */
    public MysqlTaskScheduleCatalog(AgentTaskScheduleMapper schedules) {
        this.schedules = schedules;
    }

    /**
     * cron 和延迟必须只给其中一个。不写 payload，也不填触发时间。
     * 不校验 cron 语法，因为这次还不调度。
     */
    @Override
    public TaskScheduleSummary register(String scheduleId, String taskType, String cronExpr, Integer delaySeconds) {
        String cron = cronExpr == null || cronExpr.isBlank() ? null : cronExpr;
        boolean hasCron = cron != null;
        boolean hasDelay = delaySeconds != null;
        if (scheduleId == null || scheduleId.isBlank() || scheduleId.length() > ID_LIMIT
                || taskType == null || taskType.isBlank() || taskType.length() > TYPE_LIMIT
                || !TASK_TYPES.contains(taskType)
                || (hasCron && cron.length() > CRON_LIMIT)
                || (hasDelay && delaySeconds < 1)
                || hasCron == hasDelay) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task schedule is invalid");
        }
        AgentTaskSchedule row = new AgentTaskSchedule();
        row.setScheduleId(scheduleId);
        row.setTaskType(taskType);
        row.setCronExpr(cron);
        row.setDelaySeconds(hasDelay ? delaySeconds : null);
        row.setEnabled(1);
        try {
            schedules.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule already exists");
        }
        return new TaskScheduleSummary(scheduleId, taskType, cron, hasDelay ? delaySeconds : null, true);
    }

    /**
     * 先确认存在且启用，再按启用行更新，避免覆盖已停用行。
     * 不计算下次触发，也不消费。
     */
    @Override
    public TaskScheduleSummary disable(String scheduleId) {
        AgentTaskSchedule existing = require(scheduleId);
        if (!Integer.valueOf(1).equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule is not enabled");
        }
        int updated = schedules.disableEnabled(scheduleId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule is not enabled");
        }
        return summary(existing, false);
    }

    /**
     * 先确认存在且停用，再按停用行更新，避免覆盖已启用行。
     * 不计算下次触发，也不消费。
     */
    @Override
    public TaskScheduleSummary restore(String scheduleId) {
        AgentTaskSchedule existing = require(scheduleId);
        if (Integer.valueOf(1).equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule is already enabled");
        }
        int updated = schedules.restoreDisabled(scheduleId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule is already enabled");
        }
        return summary(existing, true);
    }

    /**
     * 空结果返回空列表。不按启用再滤一层，停用也要能看见。
     * 不读任务参数，也不算下次触发。
     */
    @Override
    public List<TaskScheduleSummary> recent(int limit) {
        List<AgentTaskSchedule> rows = schedules.recent(AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new TaskScheduleSummary(
                        row.getScheduleId(),
                        row.getTaskType(),
                        row.getCronExpr(),
                        row.getDelaySeconds(),
                        Integer.valueOf(1).equals(row.getEnabled())))
                .toList();
    }

    /** 按调度标识定位一行。 */
    private AgentTaskSchedule require(String scheduleId) {
        if (scheduleId == null || scheduleId.isBlank() || scheduleId.length() > ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task schedule is invalid");
        }
        AgentTaskSchedule existing = schedules.findByScheduleId(scheduleId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "task schedule not found");
        }
        return existing;
    }

    /** 用库里的字段，只改启用标记。 */
    private static TaskScheduleSummary summary(AgentTaskSchedule existing, boolean enabled) {
        return new TaskScheduleSummary(
                existing.getScheduleId(),
                existing.getTaskType(),
                existing.getCronExpr(),
                existing.getDelaySeconds(),
                enabled);
    }
}
