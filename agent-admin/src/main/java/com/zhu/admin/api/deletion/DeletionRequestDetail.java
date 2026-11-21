package com.zhu.admin.api.deletion;

/**
 * 一条数据删除请求的详情。含提出时间与执行结果。
 *
 * @param requestId    请求标识
 * @param tenantId     租户，没有时是空串
 * @param dataType     SESSION / ATTACHMENT / ALL
 * @param status       PENDING / APPROVED / REJECTED / RUNNING / DONE / FAILED
 * @param reason       原因，没有时为空
 * @param requestedAt  提出时间，UTC，没有时区后缀；没有时为空串
 * @param executedAt   执行时间，UTC，没有时区后缀；还没执行时为空串
 * @param executedNote 执行结果摘要；还没执行时为空串
 */
public record DeletionRequestDetail(
        String requestId,
        String tenantId,
        String dataType,
        String status,
        String reason,
        String requestedAt,
        String executedAt,
        String executedNote) {
}
