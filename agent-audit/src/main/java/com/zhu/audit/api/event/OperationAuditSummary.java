package com.zhu.audit.api.event;

/**
 * 一条已登记的后台操作。不带前后快照，也不表示业务已经按它改过。
 *
 * @param operatorId 操作人
 * @param tenantId   租户，没有时是空串
 * @param operation  动作
 * @param objectType 对象类型
 * @param objectId   对象标识，没有时为空
 */
public record OperationAuditSummary(
        String operatorId,
        String tenantId,
        String operation,
        String objectType,
        String objectId) {
}
