package com.zhu.audit.api;

import java.util.List;

/**
 * 登记用户附件元数据和引用。不收文件，也不接对象存储。
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

    /**
     * 把附件挂到一条消息、一次运行或一篇知识文档上。附件必须属于这个用户。
     * 同一附件、类型和标识再挂一次是冲突。不核对话、运行或文档是否存在。
     */
    AttachmentRefSummary link(String userId, String attachmentId, String refType, String refId);

    /**
     * 当前用户挂到该引用上的存储路径。没有行返回空列表。
     * 空白租户按空串。不核文档、消息或运行是否存在。
     */
    List<String> storagePaths(String userId, String tenantId, String refType, String refId);

    /**
     * 本人的一条附件。找不到或别人的按找不到处理。
     * 不读对象存储，也不改状态。
     */
    StoredAttachment owned(String userId, String attachmentId);
}
