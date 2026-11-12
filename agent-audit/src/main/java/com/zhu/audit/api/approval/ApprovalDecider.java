package com.zhu.audit.api.approval;

/**
 * 审批决定口。只改仍为 PENDING 的单据，不负责批准后的工具执行。
 * 决定成功后关掉对应待办。
 */
public interface ApprovalDecider {

    /**
     * 写入通过或拒绝。单据不存在时失败；已经被决定过时也失败，避免覆盖。
     * MySQL 关闭时没有共享单据，调用会失败。
     *
     * @param approvalId 审批单标识
     * @param decision   只能是通过或拒绝
     * @param approver   审批人，必填
     * @param channel    通道，由调用方标明，例如控制台
     * @param reason     意见，可空
     * @return 决定之后的单据。不带工具参数
     */
    ApprovalRow decide(String approvalId, ApprovalDecision decision, String approver, String channel, String reason);
}
