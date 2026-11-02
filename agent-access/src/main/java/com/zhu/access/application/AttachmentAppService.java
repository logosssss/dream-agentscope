package com.zhu.access.application;

import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.objectstore.api.ObjectStore;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 通用附件上传。字节先入对象存储，再登记元数据。
 * JSON 只登记路径的接口仍走目录，不经过这里。
 * 目录不可用时不写对象。登记失败会尝试删掉刚写入的对象。
 */
@Service
public class AttachmentAppService {

    private static final Logger log = Logger.getLogger(AttachmentAppService.class.getName());

    /** 单文件上限，与写文件工具的 5MB 对齐。 */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final int NAME_LIMIT = 512;

    private static final int MIME_LIMIT = 128;

    private final ObjectProvider<AttachmentCatalog> catalogs;

    private final ObjectStore objects;

    /** 目录可空。对象存储始终有实现。 */
    public AttachmentAppService(ObjectProvider<AttachmentCatalog> catalogs, ObjectStore objects) {
        this.catalogs = catalogs;
        this.objects = objects;
    }

    /**
     * 空文件或超限直接拒绝。storage_type 跟对象存储实现走，不信调用方。
     * 对象键是 att/{tenant}/{user}/{id}/{fileName}。
     */
    public AttachmentSummary upload(
            String userId,
            String tenantId,
            String sessionId,
            String fileName,
            String contentType,
            byte[] bytes) {
        AttachmentCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "attachment catalog is unavailable");
        }
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "file is required");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "file exceeds 5MB");
        }
        String displayName = displayFileName(fileName);
        if (displayName.length() > NAME_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid");
        }
        String mime = mimeType(contentType);
        String path = objects.put(objectKey(tenantId, userId, Ids.newId(), fileName), bytes);
        try {
            return catalog.register(
                    userId,
                    tenantId,
                    sessionId,
                    displayName,
                    objects.storageType(),
                    path,
                    mime,
                    (long) bytes.length,
                    null);
        } catch (RuntimeException ex) {
            rollback(path);
            throw ex;
        }
    }

    /**
     * 通用附件对象键。文件名只保留安全字符。
     */
    static String objectKey(String tenantId, String userId, String objectId, String fileName) {
        String tenant = blank(tenantId) ? "_" : tenantId.trim();
        String user = blank(userId) ? "_" : userId.trim();
        String id = blank(objectId) ? "_" : objectId.trim();
        return "att/" + tenant + "/" + user + "/" + id + "/" + safeFileName(fileName);
    }

    /** 去掉路径分隔与控制字符；空名用 file.bin。 */
    static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file.bin";
        }
        StringBuilder out = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char ch = fileName.charAt(i);
            if (ch == '/' || ch == '\\' || ch < ' ') {
                out.append('_');
            } else {
                out.append(ch);
            }
        }
        String cleaned = out.toString().trim();
        return cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned) ? "file.bin" : cleaned;
    }

    /** 表里仍存调用方给的原名；空则退回安全名。 */
    private static String displayFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file.bin";
        }
        return fileName.trim();
    }

    /** 空白按 octet-stream。超长按无效。 */
    private static String mimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        String mime = contentType.trim();
        if (mime.length() > MIME_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment is invalid");
        }
        return mime;
    }

    private void rollback(String path) {
        try {
            objects.delete(path);
        } catch (RuntimeException ex) {
            log.warning("attachment object rollback failed");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
