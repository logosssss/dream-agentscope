package com.zhu.access.api.controller;

import com.zhu.access.api.dto.AttachmentContent;
import com.zhu.access.api.dto.AttachmentRefRequest;
import com.zhu.access.api.dto.AttachmentRequest;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.access.application.AttachmentAppService;
import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.audit.api.AttachmentRefSummary;
import com.zhu.audit.api.AttachmentSummary;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前用户登记附件元数据和引用，并下载已落入对象存储的正文。
 * JSON 只记声称的路径；multipart 才把字节写入对象存储。
 */
@RestController
public class AttachmentController {

    private final ObjectProvider<AttachmentCatalog> catalogs;

    private final AttachmentAppService uploads;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AttachmentController(
            ObjectProvider<AttachmentCatalog> catalogs,
            AttachmentAppService uploads,
            AccessAccountGate accounts) {
        this.catalogs = catalogs;
        this.uploads = uploads;
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
     * multipart：file 必填；sessionId 可选。不超过 5MB。
     * 存储类型跟对象存储实现走，不接受调用方声称的路径。
     */
    @PostMapping(value = "/api/attachments/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<AttachmentSummary> upload(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestPart("file") MultipartFile file) {
        accounts.requireActive();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "file is required");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "file cannot be read");
        }
        return ApiResult.ok(uploads.upload(
                UserContext.get(),
                TenantContext.get(),
                sessionId,
                file.getOriginalFilename(),
                file.getContentType(),
                bytes));
    }

    /**
     * 只下载本人已落到对象存储的文件。失败仍走统一 JSON。
     */
    @GetMapping("/api/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable String attachmentId) {
        accounts.requireActive();
        AttachmentContent file = uploads.download(UserContext.get(), attachmentId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(file.mimeType());
        } catch (RuntimeException ex) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
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
