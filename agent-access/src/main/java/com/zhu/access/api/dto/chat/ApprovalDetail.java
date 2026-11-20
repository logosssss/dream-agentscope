package com.zhu.access.api.dto.chat;

/**
 * 本人本租户的一张审批详情。含已决定行和申请、决定时间，仍不带工具参数和执行结果。
 *
 * @param approvalId  审批单标识
 * @param toolId      工具名
 * @param sessionId   会话
 * @param decision    PENDING / APPROVED / REJECTED / EXPIRED
 * @param requestedAt 申请时间，UTC，没有时区后缀；没有时为空串
 * @param decidedAt   决定时间，UTC，没有时区后缀；待处理时为空串
 * @param reason      意见，没有时为空
 */
public record ApprovalDetail(
        String approvalId,
        String toolId,
        String sessionId,
        String decision,
        String requestedAt,
        String decidedAt,
        String reason) {
}
