package com.zhu.runtime.infrastructure.approval;

import io.agentscope.core.message.ToolUseBlock;

/**
 * 运行层：框架 Gate ASK 后待人批的一条（进程内）。
 * {@code approvalId} 取工具调用 id；含 {@link ToolUseBlock} 供续跑组装 ConfirmResult。
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
