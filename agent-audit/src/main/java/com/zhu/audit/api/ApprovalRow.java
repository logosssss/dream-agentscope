package com.zhu.audit.api;

import java.time.LocalDateTime;

/**
 * 给管理进程看的一条审批。不带工具参数。
 *
 * @param approvalId  审批单标识
 * @param userId      发起人
 * @param sessionId   会话
 * @param toolName    工具名
 * @param decision    PENDING、APPROVED、REJECTED 或 EXPIRED
 * @param requestedAt 申请时间，内存实现里为空
 */
public record ApprovalRow(
        String approvalId,
        String userId,
        String sessionId,
        String toolName,
        String decision,
        LocalDateTime requestedAt
) {
}
