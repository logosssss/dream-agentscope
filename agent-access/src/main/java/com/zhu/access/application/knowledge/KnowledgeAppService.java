package com.zhu.access.application.knowledge;

import com.zhu.access.api.dto.knowledge.KnowledgeBaseDetail;
import com.zhu.access.api.dto.knowledge.KnowledgeDocumentDetail;
import com.zhu.audit.api.attachment.AttachmentCatalog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.objectstore.api.ObjectStore;
import com.zhu.state.api.knowledge.KnowledgeBaseItem;
import com.zhu.state.api.knowledge.KnowledgeBaseOwned;
import com.zhu.state.api.knowledge.KnowledgeDocumentItem;
import com.zhu.state.api.knowledge.KnowledgeDocumentVersion;
import com.zhu.state.api.knowledge.KnowledgeRepository;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 知识库接入用例。只落库并异步触发索引，不在请求线程里嵌入。
 * 删除时先清向量和对象，再删 MySQL，避免孤儿正文仍可被检索回表。
 * 文件上传：正文进 kb_document；原始字节进对象存储；附件表记实现对应的 storage_type。
 */
@Service
public class KnowledgeAppService {

    private static final Logger log = Logger.getLogger(KnowledgeAppService.class.getName());

    /** 正文 UTF-8 字节上限。在 DTO 入口拒绝。 */
    public static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;

    /** 文档标识列宽。超长按请求错误，避免截断后对上另一篇。 */
    private static final int DOC_ID_LIMIT = 64;

    /** 知识库标识列宽。超长按请求错误，避免截断后对上另一个库。 */
    private static final int KB_ID_LIMIT = 64;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String REF_DOCUMENT = "DOCUMENT";

    private final ObjectProvider<KnowledgeRepository> repositories;

    private final KnowledgeIndexLauncher indexer;

    private final VectorStore vectors;

    private final ObjectStore objects;

    private final ObjectProvider<AttachmentCatalog> attachments;

    private final String defaultAgentId;

    /** 删向量时与索引/检索同一集合，跟 agentscope.milvus.collection 对齐。 */
    private final String knowledgeCollection;

    public KnowledgeAppService(
            ObjectProvider<KnowledgeRepository> repositories,
            KnowledgeIndexLauncher indexer,
            VectorStore vectors,
            ObjectStore objects,
            ObjectProvider<AttachmentCatalog> attachments,
            @Value("${agentscope.runtime.agent-id:companion-v1}") String defaultAgentId,
            @Value("${agentscope.milvus.collection:knowledge}") String knowledgeCollection) {
        this.repositories = repositories;
        this.indexer = indexer;
        this.vectors = vectors;
        this.objects = objects;
        this.attachments = attachments;
        this.defaultAgentId = defaultAgentId == null || defaultAgentId.isBlank() ? "companion-v1" : defaultAgentId;
        this.knowledgeCollection = MilvusCollections.resolveKnowledge(knowledgeCollection);
    }

    /** 显式建库。 */
    public KnowledgeBaseItem createBase(String tenantId, String userId, String name, String agentId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        String agent = blank(agentId) ? defaultAgentId : agentId.trim();
        return repo.createBase(Ids.newId(), tenantId, userId, agent, name);
    }

    /** 本人名下的库。每行带建库时间，不读文档。 */
    public List<KnowledgeBaseItem> listBases(String tenantId, String userId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        return repo.listBases(tenantId, userId);
    }

    /**
     * 本人的一个库。空标识或超长拒绝。找不到或不是本人按找不到。
     * 带创建时间。不读文档。
     */
    public KnowledgeBaseDetail getBase(String tenantId, String userId, String kbId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        if (blank(kbId) || kbId.length() > KB_ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledge base is invalid");
        }
        KnowledgeBaseOwned base = repo.findOwnedBase(kbId, tenantId, userId);
        if (base == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
        String created = base.createdAt() == null ? "" : TIME.format(base.createdAt());
        return new KnowledgeBaseDetail(
                base.kbId(), base.tenantId(), base.userId(), base.agentId(), base.name(), created);
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
        registerFileAttachment(tenantId, userId, fileName, contentType, bytes, saved);
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

    /**
     * 本人最新一版。空标识或超长拒绝。别人的按找不到。
     * 不读正文，不改状态，也不重新索引。
     */
    public KnowledgeDocumentDetail document(String tenantId, String userId, String docId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        if (blank(docId) || docId.length() > DOC_ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "document is invalid");
        }
        KnowledgeDocumentVersion latest = repo.findLatestDocument(docId, tenantId, userId);
        if (latest == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
        String created = latest.createdAt() == null ? "" : TIME.format(latest.createdAt());
        String updated = latest.updatedAt() == null ? "" : TIME.format(latest.updatedAt());
        return new KnowledgeDocumentDetail(
                latest.docId(),
                latest.version(),
                latest.kbId(),
                latest.agentId(),
                latest.title(),
                latest.status(),
                latest.errorMsg(),
                created,
                updated);
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

    /** 删文档全部版本、向量和对象。找不到按 NOT_FOUND。 */
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
        dropObjects(tenantId, userId, docId);
        if (repo.deleteDocument(docId, tenantId, userId) < 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
    }

    /** 删库及其文档、切块、向量和对象。 */
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
        List<KnowledgeDocumentItem> docs = repo.listLatestDocuments(kbId, tenantId, userId);
        if (docs != null) {
            for (KnowledgeDocumentItem doc : docs) {
                if (doc != null && !blank(doc.docId())) {
                    dropObjects(tenantId, userId, doc.docId());
                }
            }
        }
        if (repo.deleteBase(kbId, tenantId, userId) < 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
    }

    /**
     * 原始字节先写入对象存储，再登记附件并挂 DOCUMENT。
     * 对象写入或目录登记失败只记日志，不回滚已落库的文档正文。
     */
    private void registerFileAttachment(
            String tenantId,
            String userId,
            String fileName,
            String contentType,
            byte[] bytes,
            KnowledgeDocumentItem saved) {
        if (saved == null || blank(saved.docId())) {
            return;
        }
        String name = blank(fileName) ? saved.docId() + ".txt" : fileName.trim();
        String path;
        try {
            path = objects.put(objectKey(tenantId, userId, saved.docId(), saved.version(), name), bytes);
        } catch (RuntimeException ex) {
            log.warning("knowledge file object store failed");
            return;
        }
        AttachmentCatalog catalog = attachments.getIfAvailable();
        if (catalog == null) {
            return;
        }
        try {
            var att = catalog.register(
                    userId,
                    tenantId,
                    null,
                    name,
                    objects.storageType(),
                    path,
                    blank(contentType) ? "text/plain" : contentType.trim(),
                    bytes == null ? 0L : bytes.length,
                    null);
            catalog.link(userId, att.attachmentId(), REF_DOCUMENT, saved.docId());
        } catch (RuntimeException ex) {
            log.warning("knowledge file attachment register failed");
        }
    }

    /**
     * 知识文件对象键。文件名只保留安全字符，避免路径穿越。
     */
    static String objectKey(String tenantId, String userId, String docId, int version, String fileName) {
        String tenant = blank(tenantId) ? "_" : tenantId.trim();
        String user = blank(userId) ? "_" : userId.trim();
        String doc = blank(docId) ? "_" : docId.trim();
        return "kb/" + tenant + "/" + user + "/" + doc + "/v" + version + "/" + safeFileName(fileName);
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

    /**
     * 按 DOCUMENT 引用找到对象键再删。目录没有或读失败就跳过。
     * 单个对象删失败只记日志，不挡后续路径和 MySQL 清理。
     */
    private void dropObjects(String tenantId, String userId, String docId) {
        AttachmentCatalog catalog = attachments.getIfAvailable();
        if (catalog == null || blank(docId)) {
            return;
        }
        List<String> paths;
        try {
            paths = catalog.storagePaths(userId, tenantId, REF_DOCUMENT, docId);
        } catch (RuntimeException ex) {
            log.warning("knowledge attachment path lookup failed");
            return;
        }
        if (paths == null || paths.isEmpty()) {
            return;
        }
        for (String path : paths) {
            if (blank(path)) {
                continue;
            }
            try {
                objects.delete(path);
            } catch (RuntimeException ex) {
                log.warning("knowledge object delete failed");
            }
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
                vectors.deleteById(knowledgeCollection, id);
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
