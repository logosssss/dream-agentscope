package com.zhu.audit.application;

import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.audit.infrastructure.persistence.AttachmentMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 登记用户附件元数据。只在存储开关打开时创建。不收文件，也不写引用。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlAttachmentCatalog implements AttachmentCatalog {

    private static final String ACTIVE = "ACTIVE";

    private static final String DEFAULT_STORAGE = "LOCAL";

    private static final int USER_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final int SESSION_LIMIT = 128;

    private static final int NAME_LIMIT = 512;

    private static final int PATH_LIMIT = 1024;

    private static final int MIME_LIMIT = 128;

    private static final int CHECKSUM_LIMIT = 64;

    private static final Set<String> STORAGE_TYPES = Set.of("LOCAL", "S3", "OSS", "MINIO");

    private final AttachmentMapper attachments;

    /** 附件标识由本类生成。不依赖外部传入。 */
    public MysqlAttachmentCatalog(AttachmentMapper attachments) {
        this.attachments = attachments;
    }

    /**
     * 空白租户按空串。空白存储类型按 LOCAL。空白大小按 0。
     * 不截断超长字段。不校验路径上有没有文件。
     */
    @Override
    public AttachmentSummary register(
            String userId,
            String tenantId,
            String sessionId,
            String fileName,
            String storageType,
            String storagePath,
            String mimeType,
            Long sizeBytes,
            String checksum) {
        String savedType = storageType == null || storageType.isBlank() ? DEFAULT_STORAGE : storageType;
        long savedSize = sizeBytes == null ? 0L : sizeBytes;
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || (sessionId != null && sessionId.length() > SESSION_LIMIT)
                || fileName == null || fileName.isBlank() || fileName.length() > NAME_LIMIT
                || !STORAGE_TYPES.contains(savedType)
                || storagePath == null || storagePath.isBlank() || storagePath.length() > PATH_LIMIT
                || (mimeType != null && mimeType.length() > MIME_LIMIT)
                || savedSize < 0
                || (checksum != null && checksum.length() > CHECKSUM_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        String savedSession = sessionId == null || sessionId.isBlank() ? null : sessionId;
        String savedMime = mimeType == null || mimeType.isBlank() ? null : mimeType;
        String savedChecksum = checksum == null || checksum.isBlank() ? null : checksum;
        String attachmentId = Ids.newId();
        Attachment row = new Attachment();
        row.setAttachmentId(attachmentId);
        row.setTenantId(savedTenant);
        row.setUserId(userId);
        row.setSessionId(savedSession);
        row.setFileName(fileName);
        row.setStorageType(savedType);
        row.setStoragePath(storagePath);
        row.setMimeType(savedMime);
        row.setSizeBytes(savedSize);
        row.setChecksum(savedChecksum);
        row.setStatus(ACTIVE);
        attachments.insert(row);
        return new AttachmentSummary(
                attachmentId, savedTenant, userId, savedSession, fileName, savedType, storagePath, ACTIVE);
    }
}
