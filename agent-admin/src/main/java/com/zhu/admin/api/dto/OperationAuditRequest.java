package com.zhu.admin.api.dto;

/**
 * 登记一条后台操作。不带前后快照。
 *
 * @param operatorId 操作人
 * @param tenantId   租户，空白按空串
 * @param operation  动作
 * @param objectType 对象类型
 * @param objectId   对象标识，可以空
 */
public record OperationAuditRequest(
        String operatorId,
        String tenantId,
        String operation,
        String objectType,
        String objectId) {
}
