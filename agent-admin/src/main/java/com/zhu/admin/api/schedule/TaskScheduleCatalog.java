package com.zhu.admin.api.schedule;

import java.util.List;

/**
 * 管理进程登记、停用、恢复和查看任务调度。
 *
 * <p>本进程只登记与改状态，**不触发**：触发在用户进程（`TaskScheduleFirer`），
 * 因为发布一条任务要落到 `agent_task`，而那张表归接入层。
 */
public interface TaskScheduleCatalog {

    /**
     * 新增一条。cron 和延迟必须只给其中一个，并且给出归属用户——没有用户的调度永远不会触发。
     * 登记时就算好下次触发时间。同一标识再登记一次是冲突。不写任务参数。
     */
    TaskScheduleSummary register(String scheduleId, String taskType, String tenantId, String userId,
                                 String agentId, String cronExpr, Integer delaySeconds);

    /**
     * 停用仍启用的调度。没有这一行或已停用由实现返回失败。
     * 停用后不再触发，但下次触发时间保留原值。
     */
    TaskScheduleSummary disable(String scheduleId);

    /**
     * 恢复已停用的调度。恢复时按现在重算下次触发，避免一恢复就补触发一堆。
     * 没有这一行或已启用由实现返回失败。
     */
    TaskScheduleSummary restore(String scheduleId);

    /**
     * 最近登记的任务调度，含停用。条数默认由调用方给出，实现侧再压到上限。
     * 含下次 / 上次触发时间与最近一次状态。
     */
    List<TaskScheduleSummary> recent(int limit);
}
