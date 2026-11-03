package com.zhu.access.api.dto;

/**
 * 用户提交的审批决定。审批人取请求头用户，通道由接入层固定为 WEB，不从请求体读取。
 *
 * @param decision APPROVED 或 REJECTED
 * @param reason   意见，可空
 */
public record ApprovalDecisionRequest(String decision, String reason) {
}
