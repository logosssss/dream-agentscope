package com.zhu.access.api.dto;

/**
 * 交给当前用户看的一条待确认。只有单号和工具名，不带工具参数，也不带别人的身份。
 *
 * @param approvalId 审批单标识
 * @param toolId     待确认的工具
 */
public record PendingApproval(String approvalId, String toolId) {
}
