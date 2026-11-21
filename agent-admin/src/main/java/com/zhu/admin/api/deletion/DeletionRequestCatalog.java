package com.zhu.admin.api.deletion;

import java.util.List;

/**
 * 登记数据删除请求。决定与执行都发生在别的进程：
 * 本接口只负责读，执行结果由用户进程写回同一张表。
 */
public interface DeletionRequestCatalog {

    /**
     * 记下一条 PENDING。请求标识由实现生成。
     */
    DeletionRequestSummary submit(String userId, String tenantId, String dataType, String reason);

    /**
     * 当前用户在这个租户下的删除请求。默认 50 条，最多 100 条。
     * 含已决定与已执行的行。
     */
    List<DeletionRequestSummary> owned(String userId, String tenantId, int limit);

    /**
     * 当前用户在这个租户下的一条删除请求。别人的或没有时按找不到。
     * 含提出时间与执行结果。
     */
    DeletionRequestDetail ownedOne(String userId, String tenantId, String requestId);

    /**
     * 管理端看近期请求。status 为空或 ALL 表示不过滤，默认 50 条、最多 100 条。
     * 批准之后靠这张列表看执行结果（DONE / FAILED 与 executedNote）。
     */
    List<DeletionRequestSummary> recent(String status, int limit);
}
