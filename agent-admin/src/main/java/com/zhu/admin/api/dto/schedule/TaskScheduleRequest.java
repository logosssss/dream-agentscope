package com.zhu.admin.api.dto.schedule;

/**
 * 登记一条任务调度。
 *
 * @param scheduleId    调度标识
 * @param taskType      BACKFILL / REPORT / BATCH
 * @param tenantId      租户；可为空
 * @param userId        归属用户；必填，触发时发布任务要带
 * @param agentId       目标 Agent；可空，空表示运行配置的默认 Agent
 * @param cronExpr      cron 表达式，和 delaySeconds 二选一
 * @param delaySeconds  延迟秒数，从 1 起
 */
public record TaskScheduleRequest(
        String scheduleId,
        String taskType,
        String tenantId,
        String userId,
        String agentId,
        String cronExpr,
        Integer delaySeconds) {
}
