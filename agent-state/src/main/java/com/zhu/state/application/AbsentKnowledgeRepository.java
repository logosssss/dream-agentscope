package com.zhu.state.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.state.api.KnowledgeBaseItem;
import com.zhu.state.api.KnowledgeChunkItem;
import com.zhu.state.api.KnowledgeDocumentItem;
import com.zhu.state.api.KnowledgeRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时知识库不可用。只给测试装配，不改记到内存。
 */
@Service
@ConditionalOnProperty(
        name = "agentscope.distributed.mysql.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class AbsentKnowledgeRepository implements KnowledgeRepository {

    @Override
    public KnowledgeBaseItem createBase(String kbId, String tenantId, String userId, String agentId, String name) {
        throw new BusinessException(ErrorCode.UNAVAILABLE, "knowledge repository is unavailable");
    }

    @Override
    public KnowledgeBaseItem findBase(String kbId, String tenantId, String userId) {
        return null;
    }

    @Override
    public List<KnowledgeBaseItem> listBases(String tenantId, String userId) {
        return List.of();
    }

    @Override
    public List<KnowledgeDocumentItem> listLatestDocuments(String kbId, String tenantId, String userId) {
        return List.of();
    }

    @Override
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
        throw new BusinessException(ErrorCode.UNAVAILABLE, "knowledge repository is unavailable");
    }

    @Override
    public KnowledgeDocumentItem findDocument(String docId, int version, String tenantId, String userId) {
        return null;
    }

    @Override
    public int latestVersion(String docId, String tenantId, String userId) {
        return 0;
    }

    @Override
    public int updateDocumentStatus(
            String docId, int version, String tenantId, String userId, String status, String errorMsg) {
        return 0;
    }

    @Override
    public int deleteChunks(String docId, int version) {
        return 0;
    }

    @Override
    public void insertChunk(KnowledgeChunkItem chunk, String tenantId, String userId, String agentId) {
        throw new BusinessException(ErrorCode.UNAVAILABLE, "knowledge repository is unavailable");
    }

    @Override
    public List<KnowledgeChunkItem> findSuccessChunksByIds(
            String tenantId, String userId, String agentId, List<String> ids) {
        return List.of();
    }

    @Override
    public List<KnowledgeChunkItem> searchSuccessByFulltext(
            String tenantId, String userId, String agentId, String query, int limit) {
        return List.of();
    }

    @Override
    public List<String> listChunkIdsByDoc(String docId, String tenantId, String userId) {
        return List.of();
    }

    @Override
    public List<String> listChunkIdsByBase(String kbId, String tenantId, String userId) {
        return List.of();
    }

    @Override
    public int deleteDocument(String docId, String tenantId, String userId) {
        return 0;
    }

    @Override
    public int deleteBase(String kbId, String tenantId, String userId) {
        return 0;
    }
}
