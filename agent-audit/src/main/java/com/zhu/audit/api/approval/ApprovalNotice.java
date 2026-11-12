package com.zhu.audit.api.approval;

/**
 * 一条已登记的审批通知。不表示消息已经送到。
 *
 * @param id         行主键
 * @param approvalId 审批单标识
 * @param receiverId 接收人
 * @param channel    这次是 WEB
 * @param sendStatus PENDING / SENT / FAILED
 */
public record ApprovalNotice(
        Long id, String approvalId, String receiverId, String channel, String sendStatus) {
}
