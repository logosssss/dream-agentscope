package com.zhu.access.api.deletion;

import java.util.List;

/**
 * 用户侧的数据删除请求：登记与查看。
 *
 * <p>本进程不批准；批准在管理进程。批准后由用户进程的删除执行器真删，
 * 状态依次是 PENDING → APPROVED → RUNNING → DONE / FAILED，
 * 执行时间与摘要写在 executedAt / executedNote 上。
 */
public interface DeletionRequestCatalog {

    /**
     * 记下一条 PENDING。请求标识由实现生成。
     */
    DeletionRequestSummary submit(String userId, String tenantId, String dataType, String reason);

    /**
     * 当前用户在这个租户下的删除请求。默认 50 条，最多 100 条。
     * 含已决定与已执行的行，用于看进度。
     */
    List<DeletionRequestSummary> owned(String userId, String tenantId, int limit);

    /**
     * 当前用户在这个租户下的一条删除请求。别人的或没有时按找不到。
     * 含提出时间与执行结果。
     */
    DeletionRequestDetail ownedOne(String userId, String tenantId, String requestId);
}
