package com.zhu.audit.api;

import java.util.List;

/**
 * 管理进程登记和查看任务调度。不计算下次触发，也不消费。
 */
public interface TaskScheduleCatalog {

    /**
     * 新增一条。cron 和延迟必须只给其中一个。
     * 同一标识再登记一次是冲突。不写任务参数。
     */
    TaskScheduleSummary register(String scheduleId, String taskType, String cronExpr, Integer delaySeconds);

    /**
     * 最近登记的任务调度，含停用。条数默认由调用方给出，实现侧再压到上限。
     * 不读任务参数，也不触发。
     */
    List<TaskScheduleSummary> recent(int limit);
}
