package com.zhu.access.application;

import com.zhu.access.api.dto.RunSummary;
import java.util.List;

/**
 * 当前用户的运行记录查询。MySQL 关闭时没有这个实现。
 * 只读已经结束的行，不返回步骤或全文。
 */
public interface RunQuery {

    /**
     * 当前用户在这个租户下最近的运行。默认 50 条，最多 100 条。
     * 不把别人的行带出来，也不读步骤表。
     */
    List<RunSummary> owned(String userId, String tenantId, int limit);
}
