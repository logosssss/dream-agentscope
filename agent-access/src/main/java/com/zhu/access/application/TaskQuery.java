package com.zhu.access.application;

import com.zhu.access.api.dto.TaskSummary;
import java.util.List;

/**
 * 当前用户的同步任务查询。MySQL 关闭时没有这个实现。
 * 只读已经落下的状态，不返回任务参数。
 */
public interface TaskQuery {

    /**
     * 当前用户在这个租户下最近的任务。默认 50 条，最多 100 条。
     * 不把别人的行带出来，也不读 payload。
     */
    List<TaskSummary> owned(String userId, String tenantId, int limit);
}
