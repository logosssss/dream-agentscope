package com.zhu.admin.api.dto;

/**
 * 登记一条任务调度。不触发执行。
 *
 * @param scheduleId    调度标识
 * @param taskType      BACKFILL / REPORT / BATCH
 * @param cronExpr      cron 表达式，和 delaySeconds 二选一
 * @param delaySeconds  延迟秒数，从 1 起
 */
public record TaskScheduleRequest(String scheduleId, String taskType, String cronExpr, Integer delaySeconds) {
}
