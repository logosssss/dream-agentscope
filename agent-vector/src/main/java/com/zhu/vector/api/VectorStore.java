package com.zhu.vector.api;

import java.util.List;

/**
 * 向量存取口。只写向量和按过滤条件近邻。
 * 不拼提示词，也不做知识切片。
 */
public interface VectorStore {

    /**
     * 按主键写入或覆盖一条向量。身份字段必须带上。
     */
    void upsert(VectorRecord record);

    /**
     * 在同一身份范围内近邻。没有命中时返回空列表。
     */
    List<VectorHit> search(VectorQuery query);

    /**
     * 按主键删除。集合或 id 为空时拒绝。
     */
    void deleteById(String collection, String id);

    /**
     * 按租户、用户和 Agent 删除。知识集合没有租户字段，租户传空串。
     */
    void deleteByScope(String collection, String tenantId, String userId, String agentId);
}
