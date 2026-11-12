package com.zhu.admin.api.dto.approval;

/**
 * 控制台提交的审批决定。通道由管理进程固定，不从请求体读取。
 *
 * @param decision APPROVED 或 REJECTED
 * @param approver 审批人
 * @param reason   意见，可空
 */
public record ApprovalDecisionRequest(String decision, String approver, String reason) {
}
