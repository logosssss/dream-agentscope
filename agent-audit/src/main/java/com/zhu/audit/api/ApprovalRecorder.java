package com.zhu.audit.api;

/**
 * 审批记录口。ASK 决定落库走这里，推送由接入层订阅事件完成。
 */
public interface ApprovalRecorder {

    /**
     * 记下一次待审批。决策先写成待处理，批准或拒绝以后再更新。
     *
     * @param approvalId 审批单标识
     * @param toolId     待执行的工具
     * @param userId     发起人
     * @param sessionId  会话。没有会话时传空，落库写成空串
     * @param tenantId   租户。没有租户时传空，落库写成空串
     */
    void requested(String approvalId, String toolId, String userId, String sessionId, String tenantId);
}
