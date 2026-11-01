package com.zhu.access.api.controller;

import com.zhu.access.api.dto.AttachmentRefRequest;
import com.zhu.access.api.dto.AttachmentRequest;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentRefSummary;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户登记附件元数据和引用。不接收文件，也不接对象存储。
 */
@RestController
public class AttachmentController {

    private final ObjectProvider<AttachmentCatalog> catalogs;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AttachmentController(ObjectProvider<AttachmentCatalog> catalogs, AccessAccountGate accounts) {
        this.catalogs = catalogs;
        this.accounts = accounts;
    }

    /**
     * 用户取请求头。没有用户或账号不可用时拒绝。租户没有时按空串登记。
     */
    @PostMapping("/api/attachments")
    public ApiResult<AttachmentSummary> register(@RequestBody AttachmentRequest request) {
        accounts.requireActive();
        AttachmentCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "attachment catalog is unavailable");
        }
        AttachmentRequest body = request == null
                ? new AttachmentRequest(null, null, null, null, null, null, null)
                : request;
        return ApiResult.ok(catalog.register(
                UserContext.get(),
                TenantContext.get(),
                body.sessionId(),
                body.fileName(),
                body.storageType(),
                body.storagePath(),
                body.mimeType(),
                body.sizeBytes(),
                body.checksum()));
    }

    /**
     * 用户取请求头。附件必须属于这个用户。不核对话或运行。
     */
    @PostMapping("/api/attachments/{attachmentId}/refs")
    public ApiResult<AttachmentRefSummary> link(
            @PathVariable String attachmentId,
            @RequestBody AttachmentRefRequest request) {
        accounts.requireActive();
        AttachmentCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "attachment catalog is unavailable");
        }
        AttachmentRefRequest body = request == null ? new AttachmentRefRequest(null, null) : request;
        return ApiResult.ok(catalog.link(UserContext.get(), attachmentId, body.refType(), body.refId()));
    }
}
