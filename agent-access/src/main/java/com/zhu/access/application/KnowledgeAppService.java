package com.zhu.access.application;

import com.zhu.audit.api.AttachmentCatalog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.state.api.KnowledgeBaseItem;
import com.zhu.state.api.KnowledgeDocumentItem;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 知识库接入用例。只落库并异步触发索引，不在请求线程里嵌入。
 * 删除时先清向量再删 MySQL，避免孤儿正文仍可被检索回表。
 * 文件上传只收纯文本，正文进 kb_document；附件表只记元数据，不接对象存储。
 */
@Service
public class KnowledgeAppService {

    private static final Logger log = Logger.getLogger(KnowledgeAppService.class.getName());

    /** 正文 UTF-8 字节上限。在 DTO 入口拒绝。 */
    public static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;

    private static final String REF_DOCUMENT = "DOCUMENT";

    private final ObjectProvider<KnowledgeRepository> repositories;

    private final KnowledgeIndexLauncher indexer;

    private final VectorStore vectors;

    private final ObjectProvider<AttachmentCatalog> attachments;

    private final String defaultAgentId;

    public KnowledgeAppService(
            ObjectProvider<KnowledgeRepository> repositories,
            KnowledgeIndexLauncher indexer,
            VectorStore vectors,
            ObjectProvider<AttachmentCatalog> attachments,
            @Value("${agentscope.runtime.agent-id:companion-v1}") String defaultAgentId) {
        this.repositories = repositories;
        this.indexer = indexer;
        this.vectors = vectors;
        this.attachments = attachments;
        this.defaultAgentId = defaultAgentId == null || defaultAgentId.isBlank() ? "companion-v1" : defaultAgentId;
    }

    /** 显式建库。 */
    public KnowledgeBaseItem createBase(String tenantId, String userId, String name, String agentId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        String agent = blank(agentId) ? defaultAgentId : agentId.trim();
        return repo.createBase(Ids.newId(), tenantId, userId, agent, name);
    }

    /** 本人名下的库。 */
    public List<KnowledgeBaseItem> listBases(String tenantId, String userId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        return repo.listBases(tenantId, userId);
    }

    /** 某库下每个文档最新一版，不含正文。 */
    public List<KnowledgeDocumentItem> listDocuments(String tenantId, String userId, String kbId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        if (blank(kbId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "kbId is required");
        }
        if (repo.findBase(kbId, tenantId, userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
        List<KnowledgeDocumentItem> rows = repo.listLatestDocuments(kbId, tenantId, userId);
        List<KnowledgeDocumentItem> out = new ArrayList<>(rows.size());
        for (KnowledgeDocumentItem row : rows) {
            out.add(stripContent(row));
        }
        return out;
    }

    /**
     * 新建或更新正文。更新时归档旧 SUCCESS，新版本 PENDING 后异步索引。
     */
    public KnowledgeDocumentItem upload(
            String tenantId, String userId, String kbId, String docId, String title, String content) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        rejectTooLarge(content);
        if (blank(kbId) || blank(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "kbId and content are required");
        }
        KnowledgeBaseItem base = repo.findBase(kbId, tenantId, userId);
        if (base == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
        boolean update = !blank(docId);
        String id = update ? docId.trim() : Ids.newId();
        int version = update ? repo.latestVersion(id, tenantId, userId) + 1 : 1;
        if (update && version <= 1 && repo.latestVersion(id, tenantId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
        KnowledgeDocumentItem saved = repo.insertDocumentVersion(
                id,
                version,
                kbId,
                tenantId,
                userId,
                base.agentId(),
                title,
                content,
                update);
        indexer.enqueue(tenantId, userId, base.agentId(), saved.docId(), saved.version());
        return stripContent(saved);
    }

    /**
     * 纯文本文件进知识库：解成正文后走 upload，再尽量登记附件并挂 DOCUMENT 引用。
     * 附件目录不可用或登记失败不回滚已落库的文档。
     *
     * @param fileName    原始文件名，可空
     * @param contentType Content-Type，可空
     * @param bytes       文件字节
     */
    public KnowledgeDocumentItem uploadFile(
            String tenantId,
            String userId,
            String kbId,
            String docId,
            String title,
            String fileName,
            String contentType,
            byte[] bytes) {
        String content = KnowledgeTextFileReader.readUtf8(fileName, contentType, bytes, MAX_CONTENT_BYTES);
        String resolvedTitle = KnowledgeTextFileReader.defaultTitle(title, fileName);
        KnowledgeDocumentItem saved = upload(tenantId, userId, kbId, docId, resolvedTitle, content);
        registerFileAttachment(tenantId, userId, fileName, contentType, bytes == null ? 0L : bytes.length, saved);
        return saved;
    }

    /** 本人某一版状态。 */
    public KnowledgeDocumentItem status(String tenantId, String userId, String docId, int version) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        KnowledgeDocumentItem doc = repo.findDocument(docId, version, tenantId, userId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
        return stripContent(doc);
    }

    /** 失败版可重试索引。 */
    public KnowledgeDocumentItem reindex(String tenantId, String userId, String docId, int version) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        KnowledgeDocumentItem doc = repo.findDocument(docId, version, tenantId, userId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
        if (!KnowledgeRepository.FAILED.equals(doc.status()) && !KnowledgeRepository.PENDING.equals(doc.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "document is not retryable");
        }
        repo.updateDocumentStatus(docId, version, tenantId, userId, KnowledgeRepository.PENDING, null);
        indexer.enqueue(tenantId, userId, doc.agentId(), docId, version);
        return stripContent(repo.findDocument(docId, version, tenantId, userId));
    }

    /** 删文档全部版本与向量。找不到按 NOT_FOUND。 */
    public void deleteDocument(String tenantId, String userId, String docId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        if (blank(docId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "docId is required");
        }
        if (repo.latestVersion(docId, tenantId, userId) < 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
        dropVectors(repo.listChunkIdsByDoc(docId, tenantId, userId));
        if (repo.deleteDocument(docId, tenantId, userId) < 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
    }

    /** 删库及其文档、切块与向量。 */
    public void deleteBase(String tenantId, String userId, String kbId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        if (blank(kbId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "kbId is required");
        }
        if (repo.findBase(kbId, tenantId, userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
        dropVectors(repo.listChunkIdsByBase(kbId, tenantId, userId));
        if (repo.deleteBase(kbId, tenantId, userId) < 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
    }

    /**
     * 附件元数据路径指向知识文档版本，不表示磁盘上另有一份文件。
     * 目录缺失或写失败只记日志。
     */
    private void registerFileAttachment(
            String tenantId,
            String userId,
            String fileName,
            String contentType,
            long sizeBytes,
            KnowledgeDocumentItem saved) {
        if (saved == null || blank(saved.docId())) {
            return;
        }
        AttachmentCatalog catalog = attachments.getIfAvailable();
        if (catalog == null) {
            return;
        }
        String name = blank(fileName) ? saved.docId() + ".txt" : fileName.trim();
        String path = "kb-doc:" + saved.docId() + ":v" + saved.version();
        try {
            var att = catalog.register(
                    userId,
                    tenantId,
                    null,
                    name,
                    "LOCAL",
                    path,
                    blank(contentType) ? "text/plain" : contentType.trim(),
                    sizeBytes,
                    null);
            catalog.link(userId, att.attachmentId(), REF_DOCUMENT, saved.docId());
        } catch (RuntimeException ex) {
            log.warning("knowledge file attachment register failed");
        }
    }

    private void dropVectors(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        for (String id : chunkIds) {
            if (blank(id)) {
                continue;
            }
            try {
                vectors.deleteById(MilvusCollections.DEFAULT_KNOWLEDGE, id);
            } catch (RuntimeException ex) {
                // 向量删失败不挡 MySQL 清理；检索回表仍要求 SUCCESS，孤儿向量不可见正文。
                log.warning("knowledge vector delete failed");
            }
        }
    }

    private KnowledgeRepository requireRepo() {
        KnowledgeRepository repo = repositories.getIfAvailable();
        if (repo == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "knowledge repository is unavailable");
        }
        // Absent 实现也在容器里；create 时会抛 UNAVAILABLE
        return repo;
    }

    private static void rejectTooLarge(String content) {
        if (content == null) {
            return;
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "content exceeds 2MB");
        }
    }

    private static KnowledgeDocumentItem stripContent(KnowledgeDocumentItem doc) {
        if (doc == null) {
            return null;
        }
        return new KnowledgeDocumentItem(
                doc.docId(),
                doc.version(),
                doc.kbId(),
                doc.tenantId(),
                doc.userId(),
                doc.agentId(),
                doc.title(),
                null,
                doc.status(),
                doc.errorMsg());
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
