package com.zhu.audit.api;

/**
 * 本人的一条附件元数据。给下载用，仍不表示对象一定还在。
 *
 * @param attachmentId 附件标识
 * @param tenantId     租户，没有时是空串
 * @param userId       所属用户
 * @param fileName     原始文件名
 * @param storagePath  对象键或声称的路径
 * @param mimeType     可以空
 * @param status       ACTIVE 才允许下载
 */
public record StoredAttachment(
        String attachmentId,
        String tenantId,
        String userId,
        String fileName,
        String storagePath,
        String mimeType,
        String status) {
}
