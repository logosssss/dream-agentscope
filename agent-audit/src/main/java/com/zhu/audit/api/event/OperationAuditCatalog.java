package com.zhu.audit.api.event;

import java.util.List;

/**
 * 登记和查看后台操作审计。不改业务结果，也不写前后快照。
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

    /**
     * 最近的操作。条数默认由调用方给出，实现侧再压到上限。
     */
    List<OperationAuditSummary> recent(int limit);
}
