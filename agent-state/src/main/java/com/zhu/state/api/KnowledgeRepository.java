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

    /** 只返回本人的库。找不到返回 null。列表用，不带创建时间。 */
    KnowledgeBaseItem findBase(String kbId, String tenantId, String userId);

    /**
     * 本人的一个库，带创建时间。空标识、超长或别人的返回 null。
     * 不读文档。
     */
    KnowledgeBaseOwned findOwnedBase(String kbId, String tenantId, String userId);

    /** 本人名下的库，按创建时间倒序。 */
    List<KnowledgeBaseItem> listBases(String tenantId, String userId);

    /**
     * 某库下每个 docId 的最新一版元数据，不含正文。库不属于本人时返回空。
     */
    List<KnowledgeDocumentItem> listLatestDocuments(String kbId, String tenantId, String userId);

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

    /**
     * 本人该文档版本号最大的一行，不含正文。含已归档。
     * 空标识、超长或别人的返回 null，不抛找不到。
     */
    KnowledgeDocumentVersion findLatestDocument(String docId, String tenantId, String userId);

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

    /**
     * SUCCESS 文档上的全文检索。按相关度倒序，最多 limit 条。
     * 查询为空或 MySQL 关闭时返回空列表。
     */
    List<KnowledgeChunkItem> searchSuccessByFulltext(
            String tenantId, String userId, String agentId, String query, int limit);

    /** 本人某文档全部切块主键，删向量前先取。 */
    List<String> listChunkIdsByDoc(String docId, String tenantId, String userId);

    /** 本人某库全部切块主键。 */
    List<String> listChunkIdsByBase(String kbId, String tenantId, String userId);

    /**
     * 删掉本人某文档的全部版本与切块。找不到返回 0。
     * 不碰向量。
     */
    int deleteDocument(String docId, String tenantId, String userId);

    /**
     * 删掉本人某库及其文档与切块。找不到返回 0。
     * 不碰向量。
     */
    int deleteBase(String kbId, String tenantId, String userId);
}
