package com.zhu.audit.api;

/**
 * 一条已登记的附件引用。不表示对应消息或运行一定存在。
 *
 * @param attachmentId 附件标识
 * @param refType      MESSAGE / RUN
 * @param refId        消息或运行标识
 */
public record AttachmentRefSummary(String attachmentId, String refType, String refId) {
}
