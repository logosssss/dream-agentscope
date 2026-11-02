package com.zhu.access.api.controller;

import com.zhu.access.api.dto.KnowledgeBaseCreateRequest;
import com.zhu.access.api.dto.KnowledgeDocumentUploadRequest;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.access.application.KnowledgeAppService;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.KnowledgeBaseItem;
import com.zhu.state.api.KnowledgeDocumentItem;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户私有知识库入口。JSON 正文或纯文本文件；索引异步跑。不接 PDF/对象存储。
 */
@RestController
public class KnowledgeController {

    private final KnowledgeAppService knowledge;

    private final AccessAccountGate accounts;

    public KnowledgeController(KnowledgeAppService knowledge, AccessAccountGate accounts) {
        this.knowledge = knowledge;
        this.accounts = accounts;
    }

    /** 显式建库。 */
    @PostMapping("/api/kb")
    public ApiResult<KnowledgeBaseItem> createBase(@RequestBody(required = false) KnowledgeBaseCreateRequest request) {
        accounts.requireActive();
        KnowledgeBaseCreateRequest body =
                request == null ? new KnowledgeBaseCreateRequest(null, null) : request;
        return ApiResult.ok(knowledge.createBase(
                TenantContext.get(), UserContext.get(), body.name(), body.agentId()));
    }

    /** 本人知识库列表。 */
    @GetMapping("/api/kb")
    public ApiResult<List<KnowledgeBaseItem>> listBases() {
        accounts.requireActive();
        return ApiResult.ok(knowledge.listBases(TenantContext.get(), UserContext.get()));
    }

    /**
     * 别人的库按找不到处理。不读文档，也不读正文。
     */
    @GetMapping("/api/kb/{kbId}")
    public ApiResult<KnowledgeBaseItem> getBase(@PathVariable String kbId) {
        accounts.requireActive();
        return ApiResult.ok(knowledge.getBase(TenantContext.get(), UserContext.get(), kbId));
    }

    /** 删库及下属文档、向量和对象。 */
    @DeleteMapping("/api/kb/{kbId}")
    public ApiResult<Void> deleteBase(@PathVariable String kbId) {
        accounts.requireActive();
        knowledge.deleteBase(TenantContext.get(), UserContext.get(), kbId);
        return ApiResult.ok(null);
    }

    /** 某库文档列表（每文档最新一版，无正文）。 */
    @GetMapping("/api/kb/{kbId}/documents")
    public ApiResult<List<KnowledgeDocumentItem>> listDocuments(@PathVariable String kbId) {
        accounts.requireActive();
        return ApiResult.ok(knowledge.listDocuments(TenantContext.get(), UserContext.get(), kbId));
    }

    /**
     * 上传或更新正文。立即返回 PENDING；索引在后台。
     * 带 docId 视为更新（新 version）。
     */
    @PostMapping("/api/kb/documents")
    public ApiResult<KnowledgeDocumentItem> upload(
            @RequestBody(required = false) KnowledgeDocumentUploadRequest request) {
        accounts.requireActive();
        KnowledgeDocumentUploadRequest body =
                request == null ? new KnowledgeDocumentUploadRequest(null, null, null, null) : request;
        return ApiResult.ok(knowledge.upload(
                TenantContext.get(),
                UserContext.get(),
                body.kbId(),
                body.docId(),
                body.title(),
                body.content()));
    }

    /**
     * 纯文本文件进知识库。multipart：kbId 必填；docId/title 可选；file 必填。
     * 正文进 MySQL；附件表只记元数据并挂 DOCUMENT。不接对象存储。
     */
    @PostMapping(value = "/api/kb/documents/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<KnowledgeDocumentItem> uploadFile(
            @RequestParam("kbId") String kbId,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "title", required = false) String title,
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
        return ApiResult.ok(knowledge.uploadFile(
                TenantContext.get(),
                UserContext.get(),
                kbId,
                docId,
                title,
                file.getOriginalFilename(),
                file.getContentType(),
                bytes));
    }

    /** 轮询索引进度。 */
    @GetMapping("/api/kb/documents/{docId}/status")
    public ApiResult<KnowledgeDocumentItem> status(
            @PathVariable String docId, @RequestParam("version") int version) {
        accounts.requireActive();
        return ApiResult.ok(knowledge.status(TenantContext.get(), UserContext.get(), docId, version));
    }

    /** 失败或仍 PENDING 时可重试索引。 */
    @PostMapping("/api/kb/documents/{docId}/reindex")
    public ApiResult<KnowledgeDocumentItem> reindex(
            @PathVariable String docId, @RequestParam("version") int version) {
        accounts.requireActive();
        return ApiResult.ok(knowledge.reindex(TenantContext.get(), UserContext.get(), docId, version));
    }

    /** 删文档全部版本、向量和对象。 */
    @DeleteMapping("/api/kb/documents/{docId}")
    public ApiResult<Void> deleteDocument(@PathVariable String docId) {
        accounts.requireActive();
        knowledge.deleteDocument(TenantContext.get(), UserContext.get(), docId);
        return ApiResult.ok(null);
    }
}
