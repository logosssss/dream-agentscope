package com.zhu.audit.api;

/**
 * 一条已登记的任务调度。不表示已经触发过。
 *
 * @param scheduleId   调度标识
 * @param taskType     BACKFILL / REPORT / BATCH
 * @param cronExpr     cron 表达式，延迟任务为空
 * @param delaySeconds 延迟秒数，cron 任务为空
 * @param enabled      是否仍启用；停用后仍不调度
 */
public record TaskScheduleSummary(
        String scheduleId, String taskType, String cronExpr, Integer delaySeconds, boolean enabled) {
}
