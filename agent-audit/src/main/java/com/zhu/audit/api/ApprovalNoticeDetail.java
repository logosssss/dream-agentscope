package com.zhu.audit.api;

/**
 * 一条审批通知详情。含已发送或失败的行和创建时间，仍不表示消息已经送到。
 *
 * @param id         表主键
 * @param approvalId 审批单标识
 * @param channel    这次是 WEB
 * @param sendStatus PENDING / SENT / FAILED
 * @param createdAt  创建时间，UTC，没有时区后缀；没有时为空串
 */
public record ApprovalNoticeDetail(
        Long id, String approvalId, String channel, String sendStatus, String createdAt) {
}
