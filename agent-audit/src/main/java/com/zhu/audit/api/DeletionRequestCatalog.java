package com.zhu.audit.api;

/**
 * 登记数据删除请求。不批准，也不执行删除。
 */
public interface DeletionRequestCatalog {

    /**
     * 记下一条 PENDING。请求标识由实现生成。
     */
    DeletionRequestSummary submit(String userId, String tenantId, String dataType, String reason);
}
