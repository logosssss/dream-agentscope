package com.zhu.audit.api;

/**
 * 审批记录口。ASK 决定落库走这里，推送由接入层订阅事件完成。
 */
public interface ApprovalRecorder {

    /**
     * 记下一次待审批。
     *
     * @param approvalId 审批单标识
     * @param toolId     待执行的工具
     * @param userId     发起人
     */
    void requested(String approvalId, String toolId, String userId);
}
