package com.zhu.access.application.chat.approval;

import io.agentscope.core.message.ToolUseBlock;

/**
 * 接入层：框架 Gate ASK 后待人批的一条。
 * {@code approvalId} 取工具调用 id，供现有 {@code /api/approvals/{id}} 复用；不写审批表。
 */
public record PendingAsk(
        String approvalId,
        String tenantId,
        String userId,
        String sessionId,
        String agentId,
        String toolName,
        ToolUseBlock toolCall) {
}
