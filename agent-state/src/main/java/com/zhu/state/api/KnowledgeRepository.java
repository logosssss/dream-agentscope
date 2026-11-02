package com.zhu.state.api;

import java.util.List;

/**
 * 知识库读写缝。向量不在这里。接入层管上传，运行层管索引和检索回表。
 */
public interface KnowledgeRepository {

    String PENDING = "PENDING";
    String PROCESSING = "PROCESSING";
    String SUCCESS = "SUCCESS";
    String FAILED = "FAILED";
    String ARCHIVED = "ARCHIVED";

    /** 为当前用户建库。kbId 由调用方给出。 */
    KnowledgeBaseItem createBase(String kbId, String tenantId, String userId, String agentId, String name);

    /** 只返回本人的库。找不到返回 null。 */
    KnowledgeBaseItem findBase(String kbId, String tenantId, String userId);

    /**
     * 新建文档首版或新版本，状态 PENDING。
     * 若 archivePreviousSuccess 为 true，同 docId 下旧 SUCCESS 改 ARCHIVED。
     */
    KnowledgeDocumentItem insertDocumentVersion(
            String docId,
            int version,
            String kbId,
            String tenantId,
            String userId,
            String agentId,
            String title,
            String content,
            boolean archivePreviousSuccess);

    /** 本人文档某一版。找不到返回 null。 */
    KnowledgeDocumentItem findDocument(String docId, int version, String tenantId, String userId);

    /** 本人该 doc 最大版本号。没有行返回 0。 */
    int latestVersion(String docId, String tenantId, String userId);

    /** 改状态。可选错误摘要。行数 0 表示没改到。 */
    int updateDocumentStatus(String docId, int version, String tenantId, String userId, String status, String errorMsg);

    /** 删除某一版的全部切块，供重试前清理。 */
    int deleteChunks(String docId, int version);

    /** 写入一条切块。 */
    void insertChunk(KnowledgeChunkItem chunk, String tenantId, String userId, String agentId);

    /**
     * 按主键取切块，且所属文档版本必须是 SUCCESS，且属于该用户。
     * 向量命中后回表用。
     */
    List<KnowledgeChunkItem> findSuccessChunksByIds(String tenantId, String userId, String agentId, List<String> ids);
}
