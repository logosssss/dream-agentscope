package com.zhu.access.api.dto;

/**
 * 当前用户登记的附件元数据。不接收文件内容。
 *
 * @param sessionId    关联会话，可以空
 * @param fileName     原始文件名
 * @param storageType  LOCAL / S3 / OSS / MINIO，空白按 LOCAL
 * @param storagePath  声称的存储路径
 * @param mimeType     可以空
 * @param sizeBytes    声称的大小，空白按 0
 * @param checksum     SHA-256，可以空
 */
public record AttachmentRequest(
        String sessionId,
        String fileName,
        String storageType,
        String storagePath,
        String mimeType,
        Long sizeBytes,
        String checksum) {
}
