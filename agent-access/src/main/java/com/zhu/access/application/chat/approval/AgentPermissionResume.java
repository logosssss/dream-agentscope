package com.zhu.access.application.chat.approval;

/**
 * 接入层：Gate ASK 后人批驱动框架续跑。
 * 实现挂在 Channel Gateway，组装 {@code ConfirmResult} + {@code METADATA_CONFIRM_RESULTS}。
 */
public interface AgentPermissionResume {

    /**
     * 阻塞续跑一轮。{@code approved=true} 放行工具，{@code false} 拒绝。
     * 调用方须已从 {@link PendingAskStore} 取出单据，避免重复提交。
     */
    void resume(PendingAsk ask, boolean approved, String reason);
}
