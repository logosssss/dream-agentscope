package com.zhu.audit.api;

/**
 * 一条已登记的附件元数据。不表示文件已经落到对象存储。
 *
 * @param attachmentId 附件标识
 * @param tenantId     租户，没有时是空串
 * @param userId       所属用户
 * @param sessionId    关联会话，可以空
 * @param fileName     原始文件名
 * @param storageType  LOCAL / S3 / OSS / MINIO
 * @param storagePath  声称的存储路径
 * @param status       这次是 ACTIVE
 */
public record AttachmentSummary(
        String attachmentId,
        String tenantId,
        String userId,
        String sessionId,
        String fileName,
        String storageType,
        String storagePath,
        String status) {
}
