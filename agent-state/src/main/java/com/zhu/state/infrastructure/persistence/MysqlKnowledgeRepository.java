package com.zhu.state.infrastructure.persistence;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.KnowledgeBaseItem;
import com.zhu.state.api.KnowledgeBaseOwned;
import com.zhu.state.api.KnowledgeChunkItem;
import com.zhu.state.api.KnowledgeDocumentItem;
import com.zhu.state.api.KnowledgeDocumentVersion;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.state.infrastructure.persistence.entity.KbChunk;
import com.zhu.state.infrastructure.persistence.entity.KbDocument;
import com.zhu.state.infrastructure.persistence.entity.KbKnowledgeBase;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MySQL 打开时的知识库实现。不写向量。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlKnowledgeRepository implements KnowledgeRepository {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String ARCHIVED = "ARCHIVED";

    private static final int KB_LIMIT = 64;
    private static final int USER_LIMIT = 128;
    private static final int AGENT_LIMIT = 64;
    private static final int TENANT_LIMIT = 64;
    private static final int NAME_LIMIT = 128;
    private static final int TITLE_LIMIT = 255;
    private static final int ERROR_LIMIT = 512;

    private final KbKnowledgeBaseMapper bases;
    private final KbDocumentMapper documents;
    private final KbChunkMapper chunks;

    public MysqlKnowledgeRepository(
            KbKnowledgeBaseMapper bases, KbDocumentMapper documents, KbChunkMapper chunks) {
        this.bases = bases;
        this.documents = documents;
        this.chunks = chunks;
    }

    @Override
    public KnowledgeBaseItem createBase(String kbId, String tenantId, String userId, String agentId, String name) {
        requireIds(kbId, userId, agentId);
        if (name == null || name.isBlank() || name.length() > NAME_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledge base name is invalid");
        }
        String tenant = tenant(tenantId);
        if (bases.findOwned(kbId, tenant, userId) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "knowledge base already exists");
        }
        KbKnowledgeBase row = new KbKnowledgeBase();
        row.setKbId(kbId);
        row.setTenantId(tenant);
        row.setUserId(userId);
        row.setAgentId(agentId);
        row.setName(name.trim());
        bases.insert(row);
        return new KnowledgeBaseItem(kbId, tenant, userId, agentId, row.getName());
    }

    @Override
    public KnowledgeBaseItem findBase(String kbId, String tenantId, String userId) {
        if (blank(kbId) || blank(userId)) {
            return null;
        }
        KbKnowledgeBase row = bases.findOwned(kbId, tenant(tenantId), userId);
        return toBase(row);
    }

    /** 不读文档。超长标识按没有这行，避免截断后对上别人的库。 */
    @Override
    public KnowledgeBaseOwned findOwnedBase(String kbId, String tenantId, String userId) {
        if (blank(kbId) || kbId.length() > KB_LIMIT || blank(userId)) {
            return null;
        }
        return toOwned(bases.findOwned(kbId, tenant(tenantId), userId));
    }

    @Override
    public List<KnowledgeBaseItem> listBases(String tenantId, String userId) {
        if (blank(userId)) {
            return List.of();
        }
        List<KbKnowledgeBase> rows = bases.listOwned(tenant(tenantId), userId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<KnowledgeBaseItem> items = new ArrayList<>(rows.size());
        for (KbKnowledgeBase row : rows) {
            KnowledgeBaseItem item = toBase(row);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<KnowledgeDocumentItem> listLatestDocuments(String kbId, String tenantId, String userId) {
        if (blank(kbId) || blank(userId) || findBase(kbId, tenantId, userId) == null) {
            return List.of();
        }
        List<KbDocument> rows = documents.listLatestByKb(kbId, tenant(tenantId), userId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDocumentItem> items = new ArrayList<>(rows.size());
        for (KbDocument row : rows) {
            KnowledgeDocumentItem item = toDoc(row);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    @Transactional
    public KnowledgeDocumentItem insertDocumentVersion(
            String docId,
            int version,
            String kbId,
            String tenantId,
            String userId,
            String agentId,
            String title,
            String content,
            boolean archivePreviousSuccess) {
        requireIds(docId, userId, agentId);
        if (blank(kbId) || kbId.length() > KB_LIMIT || version < 1 || blank(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "document is invalid");
        }
        String tenant = tenant(tenantId);
        if (bases.findOwned(kbId, tenant, userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
        if (archivePreviousSuccess) {
            documents.archiveSuccess(docId, tenant, userId);
        }
        KbDocument row = new KbDocument();
        row.setDocId(docId);
        row.setVersion(version);
        row.setKbId(kbId);
        row.setTenantId(tenant);
        row.setUserId(userId);
        row.setAgentId(agentId);
        row.setTitle(title == null ? "" : clip(title.trim(), TITLE_LIMIT));
        row.setContent(content);
        row.setStatus(PENDING);
        row.setErrorMsg(null);
        documents.insert(row);
        return toDoc(row);
    }

    @Override
    public KnowledgeDocumentItem findDocument(String docId, int version, String tenantId, String userId) {
        if (blank(docId) || blank(userId) || version < 1) {
            return null;
        }
        return toDoc(documents.findOwned(docId, version, tenant(tenantId), userId));
    }

    /** 不读 content。超长标识按没有这行，避免截断后对上别人的文档。 */
    @Override
    public KnowledgeDocumentVersion findLatestDocument(String docId, String tenantId, String userId) {
        if (blank(docId) || docId.length() > KB_LIMIT || blank(userId)) {
            return null;
        }
        return toVersion(documents.findLatestOwned(docId, tenant(tenantId), userId));
    }

    @Override
    public int latestVersion(String docId, String tenantId, String userId) {
        if (blank(docId) || blank(userId)) {
            return 0;
        }
        Integer max = documents.maxVersion(docId, tenant(tenantId), userId);
        return max == null ? 0 : max;
    }

    @Override
    public int updateDocumentStatus(
            String docId, int version, String tenantId, String userId, String status, String errorMsg) {
        if (blank(docId) || blank(userId) || version < 1 || blank(status)) {
            return 0;
        }
        return documents.updateStatus(
                docId, version, tenant(tenantId), userId, status, errorMsg == null ? null : clip(errorMsg, ERROR_LIMIT));
    }

    @Override
    public int deleteChunks(String docId, int version) {
        if (blank(docId) || version < 1) {
            return 0;
        }
        return chunks.deleteByDocVersion(docId, version);
    }

    @Override
    public void insertChunk(KnowledgeChunkItem chunk, String tenantId, String userId, String agentId) {
        if (chunk == null || blank(chunk.id()) || blank(chunk.docId()) || blank(chunk.content())
                || blank(userId) || blank(agentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunk is invalid");
        }
        KbChunk row = new KbChunk();
        row.setId(chunk.id());
        row.setKbId(chunk.kbId());
        row.setDocId(chunk.docId());
        row.setVersion(chunk.version());
        row.setTenantId(tenant(tenantId));
        row.setUserId(userId);
        row.setAgentId(agentId);
        row.setContent(chunk.content());
        row.setChunkIndex(chunk.chunkIndex());
        row.setTokenCount(chunk.content().length());
        chunks.insert(row);
    }

    @Override
    public List<KnowledgeChunkItem> findSuccessChunksByIds(
            String tenantId, String userId, String agentId, List<String> ids) {
        if (blank(userId) || blank(agentId) || ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> wanted = new ArrayList<>();
        for (String id : ids) {
            if (!blank(id) && !wanted.contains(id)) {
                wanted.add(id);
            }
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        return toChunks(chunks.findSuccessByIds(tenant(tenantId), userId, agentId, wanted));
    }

    @Override
    public List<KnowledgeChunkItem> searchSuccessByFulltext(
            String tenantId, String userId, String agentId, String query, int limit) {
        if (blank(userId) || blank(agentId) || blank(query) || limit < 1) {
            return List.of();
        }
        String q = query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        int size = Math.min(limit, 50);
        return toChunks(chunks.searchSuccessByFulltext(tenant(tenantId), userId, agentId, q, size));
    }

    @Override
    public List<String> listChunkIdsByDoc(String docId, String tenantId, String userId) {
        if (blank(docId) || blank(userId)) {
            return List.of();
        }
        List<String> ids = chunks.listIdsByDoc(docId, tenant(tenantId), userId);
        return ids == null ? List.of() : ids;
    }

    @Override
    public List<String> listChunkIdsByBase(String kbId, String tenantId, String userId) {
        if (blank(kbId) || blank(userId)) {
            return List.of();
        }
        List<String> ids = chunks.listIdsByKb(kbId, tenant(tenantId), userId);
        return ids == null ? List.of() : ids;
    }

    @Override
    @Transactional
    public int deleteDocument(String docId, String tenantId, String userId) {
        if (blank(docId) || blank(userId)) {
            return 0;
        }
        String tenant = tenant(tenantId);
        if (documents.maxVersion(docId, tenant, userId) == null) {
            return 0;
        }
        chunks.deleteByDoc(docId, tenant, userId);
        return documents.deleteByDoc(docId, tenant, userId);
    }

    @Override
    @Transactional
    public int deleteBase(String kbId, String tenantId, String userId) {
        if (blank(kbId) || blank(userId)) {
            return 0;
        }
        String tenant = tenant(tenantId);
        if (bases.findOwned(kbId, tenant, userId) == null) {
            return 0;
        }
        chunks.deleteByKb(kbId, tenant, userId);
        documents.deleteByKb(kbId, tenant, userId);
        return bases.deleteOwned(kbId, tenant, userId);
    }

    private static KnowledgeBaseItem toBase(KbKnowledgeBase row) {
        if (row == null) {
            return null;
        }
        return new KnowledgeBaseItem(row.getKbId(), row.getTenantId(), row.getUserId(), row.getAgentId(), row.getName());
    }

    private static KnowledgeBaseOwned toOwned(KbKnowledgeBase row) {
        if (row == null) {
            return null;
        }
        return new KnowledgeBaseOwned(
                row.getKbId(),
                row.getTenantId(),
                row.getUserId(),
                row.getAgentId(),
                row.getName(),
                row.getCreatedAt());
    }

    private static KnowledgeDocumentVersion toVersion(KbDocument row) {
        if (row == null) {
            return null;
        }
        return new KnowledgeDocumentVersion(
                row.getDocId(),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getKbId(),
                row.getAgentId(),
                row.getTitle(),
                row.getStatus(),
                row.getErrorMsg(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static KnowledgeDocumentItem toDoc(KbDocument row) {
        if (row == null) {
            return null;
        }
        return new KnowledgeDocumentItem(
                row.getDocId(),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getKbId(),
                row.getTenantId(),
                row.getUserId(),
                row.getAgentId(),
                row.getTitle(),
                row.getContent(),
                row.getStatus(),
                row.getErrorMsg());
    }

    /** 将 SUCCESS 行转为检索块；空输入返回空列表。 */
    private static List<KnowledgeChunkItem> toChunks(List<KbChunk> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<KnowledgeChunkItem> items = new ArrayList<>(rows.size());
        for (KbChunk row : rows) {
            if (row == null) {
                continue;
            }
            items.add(new KnowledgeChunkItem(
                    row.getId(),
                    row.getKbId(),
                    row.getDocId(),
                    row.getVersion() == null ? 0 : row.getVersion(),
                    row.getTitleHint(),
                    row.getContent(),
                    row.getChunkIndex() == null ? 0 : row.getChunkIndex()));
        }
        return items;
    }

    private static void requireIds(String id, String userId, String agentId) {
        if (blank(id) || id.length() > KB_LIMIT
                || blank(userId) || userId.length() > USER_LIMIT
                || blank(agentId) || agentId.length() > AGENT_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledge identity is invalid");
        }
    }

    private static String tenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }
        if (tenantId.length() > TENANT_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenantId is invalid");
        }
        return tenantId;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String clip(String value, int limit) {
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}
