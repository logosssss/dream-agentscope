package com.zhu.access.api.deletion;

/**
 * 一条数据删除请求。status 就是真实进度：
 * PENDING（待决定）→ APPROVED / REJECTED（已决定）→ RUNNING → DONE / FAILED（已执行）。
 *
 * @param requestId    请求标识
 * @param userId       发起人
 * @param tenantId     租户，没有时是空串
 * @param dataType     SESSION / ATTACHMENT / ALL
 * @param status       PENDING / APPROVED / REJECTED / RUNNING / DONE / FAILED
 * @param reason       原因，没有时为空
 * @param executedAt   执行时间，UTC，没有时区后缀；还没执行时为空串
 * @param executedNote 执行结果摘要；还没执行时为空串
 */
public record DeletionRequestSummary(
        String requestId,
        String userId,
        String tenantId,
        String dataType,
        String status,
        String reason,
        String executedAt,
        String executedNote) {
}
