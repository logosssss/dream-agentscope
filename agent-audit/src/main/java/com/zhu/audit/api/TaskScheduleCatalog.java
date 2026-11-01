package com.zhu.audit.api;

/**
 * 登记任务调度。不计算下次触发，也不消费。
 */
public interface TaskScheduleCatalog {

    /**
     * 新增一条。cron 和延迟必须只给其中一个。
     * 同一标识再登记一次是冲突。不写任务参数。
     */
    TaskScheduleSummary register(String scheduleId, String taskType, String cronExpr, Integer delaySeconds);
}
