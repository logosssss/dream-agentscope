package com.zhu.audit.api.attachment;

/**
 * 一条附件的详情。比列表多 MIME、大小和创建时间，仍不带回校验和，也不读对象存储。
 *
 * @param attachmentId 附件标识
 * @param tenantId     租户，没有时是空串
 * @param userId       所属用户
 * @param sessionId    关联会话，可以空
 * @param fileName     原始文件名
 * @param storageType  LOCAL / S3 / OSS / MINIO
 * @param storagePath  声称的存储路径
 * @param mimeType     可以空
 * @param sizeBytes    声称的大小，可以空
 * @param status       这次是 ACTIVE
 * @param createdAt    登记时间，UTC，没有时区后缀；没有时为空串
 */
public record AttachmentDetail(
        String attachmentId,
        String tenantId,
        String userId,
        String sessionId,
        String fileName,
        String storageType,
        String storagePath,
        String mimeType,
        Long sizeBytes,
        String status,
        String createdAt) {
}
