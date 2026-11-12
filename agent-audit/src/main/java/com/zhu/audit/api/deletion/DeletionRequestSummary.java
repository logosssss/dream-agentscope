package com.zhu.audit.api.deletion;

/**
 * 一条已登记的数据删除请求。不表示已经删过数据。
 *
 * @param requestId 请求标识
 * @param userId    发起人
 * @param tenantId  租户，没有时是空串
 * @param dataType  SESSION / ATTACHMENT / ALL
 * @param status    PENDING / APPROVED / REJECTED
 * @param reason    原因，没有时为空
 */
public record DeletionRequestSummary(
        String requestId,
        String userId,
        String tenantId,
        String dataType,
        String status,
        String reason) {
}
