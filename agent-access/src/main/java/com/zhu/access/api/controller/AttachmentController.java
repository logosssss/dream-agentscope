package com.zhu.access.api.controller;

import com.zhu.access.api.dto.AttachmentRequest;
import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户登记附件元数据。不接收文件，也不写引用。
 */
@RestController
public class AttachmentController {

    private final ObjectProvider<AttachmentCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AttachmentController(ObjectProvider<AttachmentCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 用户取请求头。没有用户时拒绝。租户没有时按空串登记。
     */
    @PostMapping("/api/attachments")
    public ApiResult<AttachmentSummary> register(@RequestBody AttachmentRequest request) {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        AttachmentCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "attachment catalog is unavailable");
        }
        AttachmentRequest body = request == null
                ? new AttachmentRequest(null, null, null, null, null, null, null)
                : request;
        return ApiResult.ok(catalog.register(
                userId,
                TenantContext.get(),
                body.sessionId(),
                body.fileName(),
                body.storageType(),
                body.storagePath(),
                body.mimeType(),
                body.sizeBytes(),
                body.checksum()));
    }
}
