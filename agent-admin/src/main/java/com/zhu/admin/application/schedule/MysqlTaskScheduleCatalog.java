package com.zhu.admin.application.schedule;

import com.zhu.admin.api.schedule.TaskScheduleCatalog;
import com.zhu.admin.api.schedule.TaskScheduleSummary;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.ops.schedule.ScheduleTimes;
import com.zhu.ops.support.QueryLimits;
import com.zhu.readmodel.entity.schedule.AgentTaskSchedule;
import com.zhu.readmodel.persistence.schedule.AgentTaskScheduleMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记、停用、恢复和列出任务调度。只在存储开关打开时创建。
 *
 * <p>登记时算好 {@code next_fire_at}（cron 交给 {@link ScheduleTimes}，延迟就是加秒数），
 * 触发留给用户进程。停用只改 {@code enabled}，恢复则重算下次触发。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTaskScheduleCatalog implements TaskScheduleCatalog {

    private static final int ID_LIMIT = 128;

    private static final int TYPE_LIMIT = 64;

    private static final int CRON_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int AGENT_LIMIT = 128;

    private static final Set<String> TASK_TYPES = Set.of("BACKFILL", "REPORT", "BATCH");

    private final AgentTaskScheduleMapper schedules;

    /** 同一标识由唯一键拒绝。 */
    public MysqlTaskScheduleCatalog(AgentTaskScheduleMapper schedules) {
        this.schedules = schedules;
    }

    /**
     * cron 和延迟必须只给其中一个，并给出归属用户。不写 payload，填好下次触发。
     */
    @Override
    public TaskScheduleSummary register(String scheduleId, String taskType, String tenantId, String userId,
                                        String agentId, String cronExpr, Integer delaySeconds) {
        String cron = cronExpr == null || cronExpr.isBlank() ? null : cronExpr;
        String user = userId == null || userId.isBlank() ? null : userId;
        boolean hasCron = cron != null;
        boolean hasDelay = delaySeconds != null;
        if (scheduleId == null || scheduleId.isBlank() || scheduleId.length() > ID_LIMIT
                || taskType == null || taskType.isBlank() || taskType.length() > TYPE_LIMIT
                || !TASK_TYPES.contains(taskType)
                || (hasCron && cron.length() > CRON_LIMIT)
                || (hasDelay && delaySeconds < 1)
                || hasCron == hasDelay
                || user == null || user.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || (agentId != null && !agentId.isBlank() && agentId.length() > AGENT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task schedule is invalid");
        }
        LocalDateTime nextFireAt = ScheduleTimes.nextFireAt(cron, hasDelay ? delaySeconds : null, LocalDateTime.now());
        AgentTaskSchedule row = new AgentTaskSchedule();
        row.setScheduleId(scheduleId);
        row.setTaskType(taskType);
        row.setTenantId(tenantId == null ? "" : tenantId);
        row.setUserId(user);
        row.setAgentId(agentId == null || agentId.isBlank() ? null : agentId);
        row.setCronExpr(cron);
        row.setDelaySeconds(hasDelay ? delaySeconds : null);
        row.setEnabled(1);
        row.setNextFireAt(nextFireAt);
        row.setFireCount(0);
        try {
            schedules.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule already exists");
        }
        return summary(row);
    }

    /**
     * 先确认存在且启用，再按启用行更新，避免覆盖已停用行。下次触发时间不动。
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
        existing.setEnabled(0);
        return summary(existing);
    }

    /**
     * 先确认存在且停用，再按停用行更新。下次触发按现在重算，恢复不等于补触发。
     */
    @Override
    public TaskScheduleSummary restore(String scheduleId) {
        AgentTaskSchedule existing = require(scheduleId);
        if (Integer.valueOf(1).equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule is already enabled");
        }
        if (existing.getUserId() == null || existing.getUserId().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule has no user");
        }
        LocalDateTime nextFireAt = ScheduleTimes.nextFireAt(
                existing.getCronExpr(), existing.getDelaySeconds(), LocalDateTime.now());
        int updated = schedules.restoreDisabledWithNext(scheduleId, nextFireAt);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "task schedule is already enabled");
        }
        existing.setEnabled(1);
        existing.setNextFireAt(nextFireAt);
        return summary(existing);
    }

    /**
     * 空结果返回空列表。不按启用再滤一层，停用也要能看见。
     */
    @Override
    public List<TaskScheduleSummary> recent(int limit) {
        List<AgentTaskSchedule> rows = schedules.recent(QueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlTaskScheduleCatalog::summary).toList();
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

    /** 时间统一按 ISO-8601 本地时间给出，空值给空串，前端不用判 null。 */
    private static TaskScheduleSummary summary(AgentTaskSchedule row) {
        return new TaskScheduleSummary(
                row.getScheduleId(),
                row.getTaskType(),
                row.getTenantId() == null ? "" : row.getTenantId(),
                row.getUserId() == null ? "" : row.getUserId(),
                row.getAgentId() == null ? "" : row.getAgentId(),
                row.getCronExpr(),
                row.getDelaySeconds(),
                Integer.valueOf(1).equals(row.getEnabled()),
                time(row.getNextFireAt()),
                time(row.getLastFireAt()),
                row.getLastStatus() == null ? "" : row.getLastStatus());
    }

    /** 空时间给空串。 */
    private static String time(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
