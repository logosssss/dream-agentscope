package com.zhu.audit.application;

import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentDetail;
import com.zhu.audit.api.AttachmentRefSummary;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.audit.api.StoredAttachment;
import com.zhu.audit.infrastructure.persistence.AttachmentMapper;
import com.zhu.audit.infrastructure.persistence.AttachmentRefMapper;
import com.zhu.audit.infrastructure.persistence.entity.Attachment;
import com.zhu.audit.infrastructure.persistence.entity.AttachmentRef;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记、停用、恢复用户附件元数据和引用。只在存储开关打开时创建。不收文件，也不接对象存储。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlAttachmentCatalog implements AttachmentCatalog {

    private static final String ACTIVE = "ACTIVE";

    private static final String DELETED = "DELETED";

    private static final String DEFAULT_STORAGE = "LOCAL";

    private static final int USER_LIMIT = 128;

    private static final int TENANT_LIMIT = 64;

    private static final int SESSION_LIMIT = 128;

    private static final int NAME_LIMIT = 512;

    private static final int PATH_LIMIT = 1024;

    private static final int MIME_LIMIT = 128;

    private static final int CHECKSUM_LIMIT = 64;

    private static final Set<String> STORAGE_TYPES = Set.of("LOCAL", "S3", "OSS", "MINIO");

    /** DOCUMENT 挂知识库文档主键，正文在 kb_document，不表示对象存储已落地。 */
    private static final Set<String> REF_TYPES = Set.of("MESSAGE", "RUN", "DOCUMENT");

    private static final int ATTACHMENT_LIMIT = 64;

    private static final int REF_LIMIT = 128;

    private final AttachmentMapper attachments;

    private final AttachmentRefMapper refs;

    /** 附件标识由本类生成。引用挂到已经存在且属于该用户的附件上。 */
    public MysqlAttachmentCatalog(AttachmentMapper attachments, AttachmentRefMapper refs) {
        this.attachments = attachments;
        this.refs = refs;
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

    /**
     * 先确认本人本租户仍启用，再改成 DELETED。
     * 不删对象存储，也不删引用。
     */
    @Override
    public AttachmentSummary disable(String userId, String tenantId, String attachmentId) {
        Attachment existing = requireOwned(userId, tenantId, attachmentId);
        if (!ACTIVE.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "attachment is not active");
        }
        String savedTenant = existing.getTenantId() == null ? "" : existing.getTenantId();
        int updated = attachments.markDeleted(userId, savedTenant, attachmentId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "attachment is not active");
        }
        return summary(existing, DELETED);
    }

    /**
     * 先确认本人本租户已停用，再改回 ACTIVE。
     * 不读写对象存储。
     */
    @Override
    public AttachmentSummary restore(String userId, String tenantId, String attachmentId) {
        Attachment existing = requireOwned(userId, tenantId, attachmentId);
        if (!DELETED.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "attachment is not deleted");
        }
        String savedTenant = existing.getTenantId() == null ? "" : existing.getTenantId();
        int updated = attachments.markActive(userId, savedTenant, attachmentId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "attachment is not deleted");
        }
        return summary(existing, ACTIVE);
    }

    /**
     * 别人的或已停用按找不到处理，避免靠引用探测别人的文件。
     * 不核对话或运行，也不改附件状态。
     */
    @Override
    public AttachmentRefSummary link(String userId, String attachmentId, String refType, String refId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || attachmentId == null || attachmentId.isBlank() || attachmentId.length() > ATTACHMENT_LIMIT
                || refType == null || refType.isBlank() || !REF_TYPES.contains(refType)
                || refId == null || refId.isBlank() || refId.length() > REF_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment ref is invalid");
        }
        Attachment existing = attachments.findByAttachmentId(attachmentId);
        if (existing == null
                || existing.getUserId() == null
                || !userId.equals(existing.getUserId())
                || !ACTIVE.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment not found");
        }
        AttachmentRef row = new AttachmentRef();
        row.setAttachmentId(attachmentId);
        row.setRefType(refType);
        row.setRefId(refId);
        try {
            refs.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "attachment ref already exists");
        }
        return new AttachmentRefSummary(attachmentId, refType, refId);
    }

    /**
     * 空白租户按空串。类型不认识或入参无效时返回空列表，不抛错。
     * 不改附件状态。
     */
    @Override
    public List<String> storagePaths(String userId, String tenantId, String refType, String refId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || refType == null || refType.isBlank() || !REF_TYPES.contains(refType)
                || refId == null || refId.isBlank() || refId.length() > REF_LIMIT) {
            return List.of();
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        List<String> rows = attachments.listStoragePathsByRef(userId, savedTenant, refType, refId);
        return rows == null ? List.of() : rows;
    }

    /**
     * 别人的或停用的按找不到。不把路径是否真实存在交给这里判断。
     */
    @Override
    public StoredAttachment owned(String userId, String attachmentId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || attachmentId == null || attachmentId.isBlank() || attachmentId.length() > ATTACHMENT_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid");
        }
        Attachment existing = attachments.findStored(attachmentId);
        if (existing == null
                || existing.getUserId() == null
                || !userId.equals(existing.getUserId())
                || !ACTIVE.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment not found");
        }
        String tenant = existing.getTenantId() == null ? "" : existing.getTenantId();
        String name = existing.getFileName() == null || existing.getFileName().isBlank()
                ? "file.bin"
                : existing.getFileName();
        String path = existing.getStoragePath() == null ? "" : existing.getStoragePath();
        String mime = existing.getMimeType() == null || existing.getMimeType().isBlank()
                ? null
                : existing.getMimeType();
        return new StoredAttachment(
                existing.getAttachmentId(), tenant, existing.getUserId(), name, path, mime, existing.getStatus());
    }

    /**
     * 空用户拒绝。空白租户按空串，和登记时一致。
     * 停用行不出现在列表里。
     */
    @Override
    public List<AttachmentSummary> owned(String userId, String tenantId, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        List<Attachment> rows = attachments.listOwned(userId, savedTenant, AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MysqlAttachmentCatalog::summary).toList();
    }

    /**
     * 空标识拒绝。找不到、别人的或停用按找不到，避免把路径漏出去。
     */
    @Override
    public AttachmentDetail ownedOne(String userId, String tenantId, String attachmentId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || attachmentId == null || attachmentId.isBlank() || attachmentId.length() > ATTACHMENT_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        Attachment row = attachments.findOwned(userId, savedTenant, attachmentId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment not found");
        }
        return detail(row);
    }

    /** 本人本租户的一行，含停用。空白租户按空串。 */
    private Attachment requireOwned(String userId, String tenantId, String attachmentId) {
        if (userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (tenantId != null && tenantId.length() > TENANT_LIMIT)
                || attachmentId == null || attachmentId.isBlank() || attachmentId.length() > ATTACHMENT_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? "" : tenantId;
        Attachment existing = attachments.findOwnedAny(userId, savedTenant, attachmentId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment not found");
        }
        return existing;
    }

    /** 列表和登记共用同一份摘要，不带回校验和。 */
    private static AttachmentSummary summary(Attachment row) {
        return summary(row, row.getStatus());
    }

    /** 用库里的字段，只改状态。 */
    private static AttachmentSummary summary(Attachment row, String status) {
        String tenant = row.getTenantId() == null ? "" : row.getTenantId();
        return new AttachmentSummary(
                row.getAttachmentId(),
                tenant,
                row.getUserId(),
                row.getSessionId(),
                row.getFileName(),
                row.getStorageType(),
                row.getStoragePath(),
                status);
    }

    /** 详情补 MIME 和大小。仍不带回校验和。 */
    private static AttachmentDetail detail(Attachment row) {
        String tenant = row.getTenantId() == null ? "" : row.getTenantId();
        String mime = row.getMimeType() == null || row.getMimeType().isBlank() ? null : row.getMimeType();
        return new AttachmentDetail(
                row.getAttachmentId(),
                tenant,
                row.getUserId(),
                row.getSessionId(),
                row.getFileName(),
                row.getStorageType(),
                row.getStoragePath(),
                mime,
                row.getSizeBytes(),
                row.getStatus());
    }
}
