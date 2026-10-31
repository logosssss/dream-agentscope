package com.zhu.audit.api;

/**
 * 登记后台操作审计。不改业务结果，也不写前后快照。
 */
public interface OperationAuditCatalog {

    /**
     * 追加一行。空白租户按空串。对象标识可以空。
     */
    OperationAuditSummary record(
            String operatorId,
            String tenantId,
            String operation,
            String objectType,
            String objectId);
}
