package com.zhu.admin.api.schedule;

/**
 * 一条已登记的任务调度，以及它最近一次触发的样子。
 *
 * @param scheduleId   调度标识
 * @param taskType     BACKFILL / REPORT / BATCH
 * @param tenantId     租户
 * @param userId       归属用户；为空时不会被触发
 * @param agentId      目标 Agent；空表示运行配置的默认 Agent
 * @param cronExpr     cron 表达式，延迟任务为空
 * @param delaySeconds 延迟秒数，cron 任务为空
 * @param enabled      是否仍启用；停用后不再触发
 * @param nextFireAt   下次触发时间（ISO-8601），未算出时为空串
 * @param lastFireAt   上次触发时间（ISO-8601），没触发过为空串
 * @param lastStatus   最近一次触发的任务状态（SUCCEEDED / FAILED），没触发过为空串
 */
public record TaskScheduleSummary(
        String scheduleId,
        String taskType,
        String tenantId,
        String userId,
        String agentId,
        String cronExpr,
        Integer delaySeconds,
        boolean enabled,
        String nextFireAt,
        String lastFireAt,
        String lastStatus) {
}
