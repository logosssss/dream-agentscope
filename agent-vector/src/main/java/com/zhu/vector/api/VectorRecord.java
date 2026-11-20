package com.zhu.vector.api;

/**
 * 一条待写入的向量。id 由调用方给出，不让 Milvus 自动编号。
 *
 * @param collection 集合名
 * @param id         主键
 * @param vector     与集合维度一致
 * @param tenantId   保留字段；知识集合忽略（情景集合已硬删）
 * @param userId     用户，必填
 * @param agentId    Agent，必填
 */
public record VectorRecord(
        String collection,
        String id,
        float[] vector,
        String tenantId,
        String userId,
        String agentId) {
}
