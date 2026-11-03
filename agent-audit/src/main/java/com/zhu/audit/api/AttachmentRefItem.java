package com.zhu.audit.api;

/**
 * 一条已挂上的附件引用。不表示对应消息、运行或文档一定存在。
 *
 * @param refType   MESSAGE / RUN / DOCUMENT
 * @param refId     被引用的标识
 * @param createdAt 挂上的时间，UTC，没有时区后缀；没有时为空串
 */
public record AttachmentRefItem(String refType, String refId, String createdAt) {
}
