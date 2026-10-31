package com.zhu.audit.api;

/**
 * 审批记录口。ASK 决定落库走这里。接入层另订阅事件，只记未发送通知。
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

    /**
     * 占一张已批准、还没执行的单据，并写上执行时间。没有可占的单时返回 null。
     * 默认不占。测试里的空实现因此不会把工具放行。
     */
    default String claimApproved(String tenantId, String userId, String sessionId, String toolId) {
        return null;
    }

    /**
     * 回写执行结果。只接受 SUCCEEDED、FAILED、CANCELLED，不存工具输出。
     * 默认空操作。写库失败由调用方接住，不能把已经结束的工具调用改成失败。
     */
    default void executed(String approvalId, String status) {
    }
}
