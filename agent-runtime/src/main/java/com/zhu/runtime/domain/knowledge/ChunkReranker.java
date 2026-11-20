package com.zhu.runtime.domain.knowledge;

import com.zhu.state.api.knowledge.KnowledgeChunkItem;
import java.util.List;

/**
 * 运行层：Hybrid 召回后的重排缝。不连向量库。
 * 失败时应退回原顺序截断，不抛给模型线程。
 */
public interface ChunkReranker {

    /**
     * 按与 query 的相关性重排，再截到 topK。
     * candidates 为空或 query 空白时按原顺序截断。
     */
    List<KnowledgeChunkItem> rerank(String query, List<KnowledgeChunkItem> candidates, int topK);
}
