package com.zhu.access.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import com.zhu.state.api.KnowledgeBaseItem;
import com.zhu.state.api.KnowledgeDocumentItem;
import com.zhu.state.api.KnowledgeRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 知识库接入用例。只落库并异步触发索引，不在请求线程里嵌入。
 */
@Service
public class KnowledgeAppService {

    /** 正文 UTF-8 字节上限。在 DTO 入口拒绝。 */
    public static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;

    private final ObjectProvider<KnowledgeRepository> repositories;

    private final KnowledgeIndexLauncher indexer;

    private final String defaultAgentId;

    public KnowledgeAppService(
            ObjectProvider<KnowledgeRepository> repositories,
            KnowledgeIndexLauncher indexer,
            @Value("${agentscope.runtime.agent-id:companion-v1}") String defaultAgentId) {
        this.repositories = repositories;
        this.indexer = indexer;
        this.defaultAgentId = defaultAgentId == null || defaultAgentId.isBlank() ? "companion-v1" : defaultAgentId;
    }

    /** 显式建库。 */
    public KnowledgeBaseItem createBase(String tenantId, String userId, String name, String agentId) {
        KnowledgeRepository repo = requireRepo();
        requireUser(userId);
        String agent = blank(agentId) ? defaultAgentId : agentId.trim();
        return repo.createBase(Ids.newId(), tenantId, userId, agent, name);
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
