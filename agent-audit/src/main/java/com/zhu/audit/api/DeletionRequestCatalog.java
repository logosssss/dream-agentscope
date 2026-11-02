package com.zhu.audit.api;

import java.util.List;

/**
 * 登记数据删除请求。不批准，也不执行删除。
 */
public interface DeletionRequestCatalog {

    /**
     * 记下一条 PENDING。请求标识由实现生成。
     */
    DeletionRequestSummary submit(String userId, String tenantId, String dataType, String reason);

    /**
     * 当前用户在这个租户下的删除请求。默认 50 条，最多 100 条。
     * 含已决定的行，仍不执行删除。
     */
    List<DeletionRequestSummary> owned(String userId, String tenantId, int limit);
}
