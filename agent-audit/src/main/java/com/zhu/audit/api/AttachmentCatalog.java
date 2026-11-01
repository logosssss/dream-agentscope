package com.zhu.audit.api;

/**
 * 登记用户附件元数据。不收文件，也不写引用。
 */
public interface AttachmentCatalog {

    /**
     * 新增一条。标识由实现生成。空白租户按空串。
     * 不校验文件是否存在，也不改已有行。
     */
    AttachmentSummary register(
            String userId,
            String tenantId,
            String sessionId,
            String fileName,
            String storageType,
            String storagePath,
            String mimeType,
            Long sizeBytes,
            String checksum);
}
